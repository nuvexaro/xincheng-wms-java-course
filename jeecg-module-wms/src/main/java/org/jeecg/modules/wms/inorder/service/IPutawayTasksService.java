package org.jeecg.modules.wms.inorder.service;

import org.jeecg.modules.wms.wmstask.entity.WmsTasksRecords;

/**
 * @Description: 上架业务(创建上架任务、上架)
 * @Version: V1.0
 */
public interface IPutawayTasksService {

    /**
     * 创建上架任务: 入库单收货完成后, 根据该入库单的收货记录创建上架任务
     * 一条良品收货记录创建一条上架任务(不良品不上架)
     *
     * @param stockInOrderId 入库单id
     */
    void createPutawayTasks(String stockInOrderId);

    /**
     * 上架: 执行上架任务(记录上架记录、累加任务完成数量), 把库存从收货储位移到上架储位,
     * 并更新入库单明细、入库单的上架数量和状态
     *
     * @param wmsTasksRecords 上架记录(taskId、execQuantity、targetLocationCode 必填)
     */
    void putaway(WmsTasksRecords wmsTasksRecords);
}
