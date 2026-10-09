package org.jeecg.modules.wms.inorder.service;

import org.jeecg.modules.wms.wmstask.entity.WmsTasksRecords;

/**
 * @Description: 收货业务(创建收货任务、收货)
 *               任务表是收货、上架、拣货共用的, 和具体业务无关的通用方法放在 IWmsTasksService,
 *               收货自己的业务流程放在这里
 * @Version: V1.0
 */
public interface IReceiveTasksService {

    /**
     * 创建收货任务: 根据入库单明细创建收货任务, 有几条明细就创建几条任务,
     * 并把入库单及其明细的状态更新为"收货中"
     *
     * @param orderIds 入库单id, 多个以逗号分割
     * @param operator 执行人(用户id)
     */
    void createReceiveTasks(String orderIds, String operator);

    /**
     * 收货: 执行收货任务(记录收货记录、累加任务完成数量), 并更新入库单明细、入库单的收货数量和状态
     *
     * @param wmsTasksRecords 收货记录(taskId、execQuantity、inventoryAttribute、targetLocationCode 必填)
     */
    void receive(WmsTasksRecords wmsTasksRecords);
}
