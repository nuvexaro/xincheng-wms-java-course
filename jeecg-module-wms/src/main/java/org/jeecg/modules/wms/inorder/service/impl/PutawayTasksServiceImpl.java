package org.jeecg.modules.wms.inorder.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.modules.wms.config.WarehouseDictEnum;
import org.jeecg.modules.wms.inorder.entity.WmsStockInOrders;
import org.jeecg.modules.wms.inorder.service.IPutawayTasksService;
import org.jeecg.modules.wms.inorder.service.IWmsStockInOrderItemsService;
import org.jeecg.modules.wms.inorder.service.IWmsStockInOrdersService;
import org.jeecg.modules.wms.inventory.service.impl.WmsInventoryTransByPutaway;
import org.jeecg.modules.wms.inventory.vo.WmsInventoryTransParam;
import org.jeecg.modules.wms.warehouse.entity.WmsStorageLocations;
import org.jeecg.modules.wms.warehouse.service.IWmsStorageLocationsService;
import org.jeecg.modules.wms.wmstask.entity.WmsTasks;
import org.jeecg.modules.wms.wmstask.entity.WmsTasksRecords;
import org.jeecg.modules.wms.wmstask.service.IWmsTasksRecordsService;
import org.jeecg.modules.wms.wmstask.service.IWmsTasksService;
import org.jeecg.modules.wms.wmstask.util.TaskNumberUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * @Description: 上架业务(创建上架任务、上架)
 * @Version: V1.0
 */
@Slf4j
@Service
public class PutawayTasksServiceImpl implements IPutawayTasksService {

    /** 任务号前缀(和收货任务共用同一套任务号) */
    private static final String TASK_NUMBER_PREFIX = "TSK";

    @Autowired
    private IWmsStockInOrdersService wmsStockInOrdersService;
    @Autowired
    private IWmsStockInOrderItemsService wmsStockInOrderItemsService;
    @Autowired
    private IWmsTasksService wmsTasksService;
    @Autowired
    private IWmsTasksRecordsService wmsTasksRecordsService;
    @Autowired
    private IWmsStorageLocationsService wmsStorageLocationsService;
    @Autowired
    private TaskNumberUtil taskNumberUtil;
    /** 上架时的库存变更(库存变更接口有多个实现类, 这里要按具体的实现类注入) */
    @Autowired
    private WmsInventoryTransByPutaway wmsInventoryTransByPutaway;

    // ============================== 创建上架任务 ==============================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createPutawayTasks(String stockInOrderId) {
        if (oConvertUtils.isEmpty(stockInOrderId)) {
            throw new JeecgBootException("入库单id不能为空");
        }
        //1. 防止重复创建: 该入库单已经有上架任务就不再创建
        long existCount = wmsTasksService.count(new LambdaQueryWrapper<WmsTasks>()
                .eq(WmsTasks::getStockInOrderId, stockInOrderId)
                .eq(WmsTasks::getTaskType, WarehouseDictEnum.TASK_TYPE_PUTAWAY.getCode()));
        if (existCount > 0) {
            return;
        }

        //2. 查询该入库单的所有良品收货记录(只有良品才上架, 不良品留在原来的储位)
        List<WmsTasksRecords> recordsList = wmsTasksRecordsService.list(new LambdaQueryWrapper<WmsTasksRecords>()
                .eq(WmsTasksRecords::getStockInOrderId, stockInOrderId)
                .eq(WmsTasksRecords::getTaskType, WarehouseDictEnum.TASK_TYPE_RECEIVING.getCode())
                .eq(WmsTasksRecords::getInventoryAttribute, WarehouseDictEnum.INVENTORY_ATTRIBUTE_GOOD.getCode()));
        if (recordsList == null || recordsList.isEmpty()) {
            log.info("入库单{}没有良品收货记录, 不需要创建上架任务", stockInOrderId);
            return;
        }

        //3. 一条收货记录创建一条上架任务
        List<WmsTasks> taskList = new ArrayList<>();
        for (WmsTasksRecords record : recordsList) {
            WmsTasks task = new WmsTasks();
            //任务号: TSK + 年月日 + 5位序号
            task.setTaskNumber(taskNumberUtil.generate(TASK_NUMBER_PREFIX));
            //任务类型: 上架任务
            task.setTaskType(WarehouseDictEnum.TASK_TYPE_PUTAWAY.getCode());
            //任务状态: 已创建
            task.setTaskStatus(WarehouseDictEnum.TASK_STATUS_CREATED.getCode());
            //商品
            task.setProductId(record.getProductId());
            //待上架数量 = 这条收货记录的收货数量
            task.setQuantity(record.getExecQuantity());
            task.setCompletedQuantity(0);
            //来源储位 = 收货时放的储位
            task.setSourceLocationCode(record.getTargetLocationCode());
            //仓库: 上架是在同一个仓库里移动
            task.setSourceWarehouseId(record.getTargetWarehouseId());
            task.setTargetWarehouseId(record.getTargetWarehouseId());
            //批次号、保质期沿用收货记录的
            task.setBatchNumber(record.getBatchNumber());
            task.setExpiryDate(record.getExpiryDate());
            //执行人沿用收货人
            task.setOperator(record.getOperator());
            //来源单据: 入库单 + 入库明细
            task.setStockInOrderId(record.getStockInOrderId());
            task.setStockInOrderItemId(record.getStockInOrderItemId());
            taskList.add(task);
        }
        wmsTasksService.saveBatch(taskList);
    }

    // ============================== 上架 ==============================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void putaway(WmsTasksRecords wmsTasksRecords) {
        //1. 参数校验(这一步不访问数据库)
        if (wmsTasksRecords == null || oConvertUtils.isEmpty(wmsTasksRecords.getTaskId())) {
            throw new JeecgBootException("请选择要上架的任务");
        }
        Integer execQuantity = wmsTasksRecords.getExecQuantity();
        if (execQuantity == null || execQuantity <= 0) {
            throw new JeecgBootException("上架数量必须大于0");
        }
        if (oConvertUtils.isEmpty(wmsTasksRecords.getTargetLocationCode())) {
            throw new JeecgBootException("请选择目的储位");
        }

        //2. 加锁: 先锁上架任务, 再锁入库单
        //   注意: 这两条 select ... for update 必须是本事务里最先执行的两条 SQL, 前面不要加别的查询。
        //   这样同一个入库单的多次上架会排队执行, 后面汇总上架数量时不会算错
        WmsTasks task = wmsTasksService.getByIdForUpdate(wmsTasksRecords.getTaskId());
        if (task == null) {
            throw new JeecgBootException("上架任务不存在");
        }
        if (!WarehouseDictEnum.TASK_TYPE_PUTAWAY.getCode().equals(task.getTaskType())) {
            throw new JeecgBootException("任务" + task.getTaskNumber() + "不是上架任务");
        }
        WmsStockInOrders order = wmsStockInOrdersService.getByIdForUpdate(task.getStockInOrderId());
        if (order == null) {
            throw new JeecgBootException("上架任务" + task.getTaskNumber() + "对应的入库单不存在");
        }
        if (!WarehouseDictEnum.INBOUND_RECEIVED.getCode().equals(order.getStatus())
                && !WarehouseDictEnum.INBOUND_PUTAWAYING.getCode().equals(order.getStatus())) {
            throw new JeecgBootException("入库单" + order.getOrderNumber() + "不是收货完成或上架中状态, 不能上架");
        }

        //3. 目的储位校验: 必须是该任务仓库下的储位, 且不能和来源储位相同
        String targetLocationCode = wmsTasksRecords.getTargetLocationCode();
        long locationCount = wmsStorageLocationsService.count(new LambdaQueryWrapper<WmsStorageLocations>()
                .eq(WmsStorageLocations::getLocationCode, targetLocationCode)
                .eq(WmsStorageLocations::getWarehouseId, task.getTargetWarehouseId()));
        if (locationCount == 0) {
            throw new JeecgBootException("储位" + targetLocationCode + "不存在, 或者不属于该任务的仓库");
        }
        if (targetLocationCode.equals(task.getSourceLocationCode())) {
            throw new JeecgBootException("目的储位不能和来源储位相同");
        }

        //4. 来源储位、批次号、保质期以任务为准(不信任前端传的值); 上架的都是良品
        wmsTasksRecords.setSourceLocationCode(task.getSourceLocationCode());
        wmsTasksRecords.setBatchNumber(task.getBatchNumber());
        wmsTasksRecords.setExpiryDate(task.getExpiryDate());
        wmsTasksRecords.setInventoryAttribute(WarehouseDictEnum.INVENTORY_ATTRIBUTE_GOOD.getCode());
        wmsTasksRecords.setOperationTime(null);

        //5. 执行任务: 添加上架记录, 累加任务的完成数量, 上完时任务状态变为已完成; 返回最新任务数据
        WmsTasks latestTask = wmsTasksService.execute(wmsTasksRecords);

        //6. 变更库存: 原储位减少, 新储位增加
        wmsInventoryTransByPutaway.transfer(createWmsInventoryTransParam(wmsTasksRecords));

        //7. 更新入库单明细的 上架数量、状态
        wmsStockInOrderItemsService.updateShelvedStatus(latestTask.getStockInOrderItemId());

        //8. 更新入库单的 已上架总量、状态(上架中/上架完成)
        boolean isCompleted = wmsStockInOrdersService.updateShelvedStatus(latestTask.getStockInOrderId());
        if (isCompleted) {
            log.info("入库单{}已全部上架完成", order.getOrderNumber());
        }
    }

    /**
     * 根据上架记录创建库存变更参数对象
     */
    private WmsInventoryTransParam createWmsInventoryTransParam(WmsTasksRecords wmsTasksRecords) {
        WmsInventoryTransParam param = new WmsInventoryTransParam();
        param.setProductId(wmsTasksRecords.getProductId());
        param.setExecQuantity(wmsTasksRecords.getExecQuantity());
        param.setWarehouseId(wmsTasksRecords.getTargetWarehouseId());
        param.setSourceLocationCode(wmsTasksRecords.getSourceLocationCode());
        param.setTargetLocationCode(wmsTasksRecords.getTargetLocationCode());
        param.setBatchNumber(wmsTasksRecords.getBatchNumber());
        param.setExpiryDate(wmsTasksRecords.getExpiryDate());
        param.setIsSellable(WarehouseDictEnum.INVENTORY_SELLABLE.getCode());
        // 变更类型: 上架
        param.setTransactionType(WarehouseDictEnum.INVENTORY_PUTAWAY.getCode());
        param.setOperator(wmsTasksRecords.getOperator());
        param.setOperationTime(wmsTasksRecords.getOperationTime());
        // 备注: 关联的任务号
        param.setRemarks(wmsTasksRecords.getTaskNumber());
        return param;
    }
}
