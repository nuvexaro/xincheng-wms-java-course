package org.jeecg.modules.wms.waybill.task;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.modules.wms.config.SfExpressConfig;
import org.jeecg.modules.wms.config.WarehouseDictEnum;
import org.jeecg.modules.wms.outorder.entity.WmsOutOrders;
import org.jeecg.modules.wms.outorder.service.IWmsOutOrdersService;
import org.jeecg.modules.wms.waybill.service.IWmsWaybillService;
import org.jeecg.modules.wms.waybill.task.handler.WaybillThreadCallable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * 生成运单定时任务(课件 9.5.2)
 * 每 20 秒查一次"已打包且未生成运单"的出库单, 每个出库单交给线程池里的一个线程去请求顺丰下单
 */
@Component
@Slf4j
public class WaybillTaskJob {

    /** 失败后第一次重试的等待时间: 20 秒; 之后每失败一次翻倍 */
    private static final long FIRST_RETRY_DELAY_MS = 20_000L;
    /** 失败后重试的最长等待时间: 10 分钟 */
    private static final long MAX_RETRY_DELAY_MS = 10 * 60_000L;

    @Resource
    private ThreadPoolTaskExecutor waybillThreadPool;

    @Resource
    private IWmsOutOrdersService outOrdersService;

    @Resource
    private IWmsWaybillService waybillService;

    @Resource
    private SfExpressConfig sfExpressConfig;

    /** 上一轮还没执行完时, 这一轮不执行 */
    private volatile boolean isRunning = false;

    /** 上一次打印"顺丰参数没配置"提醒的时间, 控制最多一分钟提醒一次, 避免刷屏 */
    private volatile long lastConfigWarnTime = 0;

    /**
     * 正在生成运单的出库单id
     * 一个出库单请求顺丰可能超过 15 秒, 这一轮等不到结果就结束了, 但线程还在跑;
     * 不记下来的话, 下一轮会把同一个出库单再提交一次: 线程越积越多、数据库连接被占满,
     * 还可能对同一个出库单向顺丰重复下单
     */
    private final Set<String> processingOrderIds = ConcurrentHashMap.newKeySet();

    /** 同时请求顺丰的最大数量: 同时请求太多, 顺丰沙箱会断开连接(SDK 里报 response is null) */
    private final Semaphore sfPermits = new Semaphore(3);

    /**
     * 生成运单失败的出库单: key=出库单id, value=[已经连续失败的次数, 下次允许重试的时间]
     * 失败后不再每 20 秒重试, 而是等待 20秒、40秒、80秒……最长 10 分钟再试:
     * 像"电话不合法""地址为空"这类数据错误, 不改数据重试多少次都会失败, 没必要一直请求顺丰、一直刷日志;
     * 改好数据后, 最多 10 分钟会自动重试(想马上重试可以重启后端)
     * 只保存在内存里, 重启后端后清空
     */
    private final Map<String, long[]> retryStates = new ConcurrentHashMap<>();

    @Scheduled(cron = "0/20 * * * * ?")
    public void generateWaybill(){
        // 每 20 秒执行一次, 平时不打印日志; 要确认定时任务在运行, 把这个类的日志级别调成 debug
        log.debug("生成运单定时任务开始执行");
        // 顺丰参数没配置时不请求顺丰(否则每个出库单都会失败), 但要提醒有多少出库单在等着
        if (!sfExpressConfig.isConfigured()) {
            long waiting = outOrdersService.lambdaQuery()
                    .eq(WmsOutOrders::getStatus, WarehouseDictEnum.OUTBOUND_PACKED.getCode())
                    .eq(WmsOutOrders::getCreatedWaybill, WarehouseDictEnum.NOT_CREATED_WAYBILL.getCode())
                    .count();
            long now = System.currentTimeMillis();
            if (waiting > 0 && now - lastConfigWarnTime > 60_000) {
                lastConfigWarnTime = now;
                log.warn("有{}个已打包的出库单等待生成运单, 但没有配置顺丰参数(application-dev.yml 里的 wms.express_api), 所以不会生成运单", waiting);
            }
            return;
        }
        if(isRunning){
            log.debug("生成运单任务正在执行中，本次不执行");
            return;
        }
        isRunning = true;
        long startTime = System.currentTimeMillis();
        int successCount = 0;
        int total = 0;
        try {
            // 获取任务队列在等待的任务数
            int queueSize = waybillThreadPool.getQueueSize();
            //如果队列任务数过多，造成任务堆积
            if(queueSize > 1000){
                log.warn("生成运单的任务队列堆积过多: {}，请及时处理", queueSize);
            }
            // 查询出库单: 状态已打包且未创建运单, 每轮最多处理 1000 个
            List<WmsOutOrders> outOrders = outOrdersService.lambdaQuery()
                    .eq(WmsOutOrders::getStatus, WarehouseDictEnum.OUTBOUND_PACKED.getCode())
                    .eq(WmsOutOrders::getCreatedWaybill, WarehouseDictEnum.NOT_CREATED_WAYBILL.getCode())
                    .page(new Page<>(1, 1000)).getRecords();
            if(CollUtil.isEmpty(outOrders)){
                retryStates.clear();
                log.debug("没有需要创建运单的出库单(状态为已打包、且还没生成运单)");
                return;
            }
            // 清理失败记录: 已经不在待生成列表里的出库单(比如已经生成了运单、被取消了), 不用再记
            if (outOrders.size() < 1000) {
                Set<String> pendingIds = new HashSet<>();
                outOrders.forEach(o -> pendingIds.add(o.getId()));
                retryStates.keySet().retainAll(pendingIds);
            }
            long now = System.currentTimeMillis();
            List<Future<Boolean>> taskList = new ArrayList<>();
            // 遍历出库单: 创建一个任务提交给线程池，去调用顺丰接口创建运单
            for (WmsOutOrders outOrder : outOrders) {
                String orderId = outOrder.getId();
                // 之前失败过, 还没到下次重试的时间, 这一轮跳过
                long[] state = retryStates.get(orderId);
                if (state != null && now < state[1]) {
                    continue;
                }
                // 上一轮提交的还没执行完, 这一轮跳过
                if (!processingOrderIds.add(orderId)) {
                    continue;
                }
                WaybillThreadCallable callable = new WaybillThreadCallable(waybillService, orderId);
                String orderNo = outOrder.getOrderNo();
                taskList.add(waybillThreadPool.submit(() -> {
                    sfPermits.acquire();
                    try {
                        boolean success = Boolean.TRUE.equals(callable.call());
                        if (success) {
                            retryStates.remove(orderId);
                        } else {
                            recordFailure(orderId, orderNo);
                        }
                        return success;
                    } finally {
                        sfPermits.release();
                        // 执行完(不管成功失败)才移除
                        processingOrderIds.remove(orderId);
                    }
                }));
            }
            if (taskList.isEmpty()) {
                log.debug("没有到重试时间或还在执行中的出库单, 本轮不提交新任务");
                return;
            }
            // 等待每个任务的结果: 最多等 15 秒
            // 超时不取消任务: 请求顺丰的网络调用取消不掉, 强行中断反而可能出现"顺丰下单成功但运单号没保存"
            total = taskList.size();
            for (Future<Boolean> booleanFuture : taskList) {
                try {
                    if (Boolean.TRUE.equals(booleanFuture.get(15, TimeUnit.SECONDS))) {
                        successCount++;
                    }
                } catch (java.util.concurrent.TimeoutException e) {
                    log.warn("有出库单生成运单超过15秒还没完成, 继续在后台执行");
                } catch (Exception e) {
                    log.error("等待生成运单任务结果异常", e);
                }
            }
        } finally {
            isRunning = false;
        }
        if (total > 0) {
            long totalTime = System.currentTimeMillis() - startTime;
            log.info("生成运单任务执行完成，出库单数量: {}, 成功数量: {}, 总耗时: {}ms", total, successCount, totalTime);
        }
    }

    /**
     * 记录一次失败, 计算下次重试时间: 20秒、40秒、80秒……最长 10 分钟
     */
    private void recordFailure(String orderId, String orderNo) {
        long[] state = retryStates.computeIfAbsent(orderId, k -> new long[2]);
        synchronized (state) {
            state[0]++;
            long delay = FIRST_RETRY_DELAY_MS << Math.min(state[0] - 1, 10);
            delay = Math.min(delay, MAX_RETRY_DELAY_MS);
            state[1] = System.currentTimeMillis() + delay;
            log.warn("出库单{}生成运单已连续失败{}次, {}秒后再重试(失败原因见上面的报错; 改好数据后最多10分钟会自动重试, 想马上重试可以重启后端)",
                    orderNo, state[0], delay / 1000);
        }
    }
}
