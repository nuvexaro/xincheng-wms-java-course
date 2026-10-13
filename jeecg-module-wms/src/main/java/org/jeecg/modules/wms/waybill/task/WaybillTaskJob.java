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

    @Scheduled(cron = "0/20 * * * * ?")
    public void generateWaybill(){
        // 顺丰参数没配置时不执行, 否则每个出库单都会请求失败
        if (!sfExpressConfig.isConfigured()) {
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
                // 每 20 秒执行一次, 没有任务时不打印 info 日志, 避免刷屏
                log.debug("没有需要创建运单的出库单");
                return;
            }
            total = outOrders.size();
            List<Future<Boolean>> taskList = new ArrayList<>();
            // 遍历出库单: 创建一个任务提交给线程池，去调用顺丰接口创建运单
            for (WmsOutOrders outOrder : outOrders) {
                taskList.add(waybillThreadPool.submit(new WaybillThreadCallable(waybillService, outOrder.getId())));
            }
            // 等待每个任务的结果: 最多等 15 秒, 超时就取消这个任务
            for (Future<Boolean> booleanFuture : taskList) {
                try {
                    if (Boolean.TRUE.equals(booleanFuture.get(15, TimeUnit.SECONDS))) {
                        successCount++;
                    }
                } catch (Exception e) {
                    log.error("等待生成运单任务结果异常", e);
                    booleanFuture.cancel(true);
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
