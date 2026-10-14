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
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 生成运单定时任务(课件 9.5.2)
 * 每 20 秒查一次"已打包且未生成运单"的出库单, 每个出库单交给线程池里的一个线程去请求顺丰下单
 */
@Component
@Slf4j
public class WaybillTaskJob {

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

    @Scheduled(cron = "0/20 * * * * ?")
    public void generateWaybill(){
        // 每次执行都打印一行, 在控制台搜"生成运单定时任务"就能确认定时任务在运行
        log.info("生成运单定时任务开始执行");
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
            log.info("生成运单任务正在执行中，本次不执行");
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
                log.info("没有需要创建运单的出库单(状态为已打包、且还没生成运单)");
                return;
            }
            List<Future<Boolean>> taskList = new ArrayList<>();
            // 遍历出库单: 创建一个任务提交给线程池，去调用顺丰接口创建运单
            for (WmsOutOrders outOrder : outOrders) {
                String orderId = outOrder.getId();
                // 上一轮提交的还没执行完, 这一轮跳过
                if (!processingOrderIds.add(orderId)) {
                    continue;
                }
                WaybillThreadCallable callable = new WaybillThreadCallable(waybillService, orderId);
                taskList.add(waybillThreadPool.submit(() -> {
                    sfPermits.acquire();
                    try {
                        return callable.call();
                    } finally {
                        sfPermits.release();
                        // 执行完(不管成功失败)才移除, 下一轮可以重试失败的出库单
                        processingOrderIds.remove(orderId);
                    }
                }));
            }
            if (taskList.isEmpty()) {
                log.info("出库单都还在生成运单中, 本轮不提交新任务");
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
}
