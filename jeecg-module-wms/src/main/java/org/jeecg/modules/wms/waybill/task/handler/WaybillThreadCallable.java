package org.jeecg.modules.wms.waybill.task.handler;

import lombok.extern.slf4j.Slf4j;
import org.jeecg.modules.wms.waybill.service.IWmsWaybillService;

import java.util.concurrent.Callable;

/**
 * 生成运单的任务执行体(课件 9.5.2): 一个出库单一个任务, 交给线程池执行
 * 返回 true 表示这个出库单生成运单成功
 */
@Slf4j
public class WaybillThreadCallable implements Callable<Boolean> {

    /**
     * 出库单id
     */
    private final String orderId;

    /**
     * 运单service
     */
    private final IWmsWaybillService wmsWaybillService;

    public WaybillThreadCallable(IWmsWaybillService wmsWaybillService, String orderId) {
        this.orderId = orderId;
        this.wmsWaybillService = wmsWaybillService;
    }

    @Override
    public Boolean call() {
        try {
            // 执行任务处理: 请求顺丰下单, 保存运单号
            wmsWaybillService.generateWaybillForOrder(orderId);
            return true;
        } catch (Exception e) {
            // 一个出库单失败不影响其它出库单; 记录原因, 下一轮定时任务会再试
            log.error("出库单{}生成运单失败: {}", orderId, e.getMessage(), e);
            return false;
        }
    }
}
