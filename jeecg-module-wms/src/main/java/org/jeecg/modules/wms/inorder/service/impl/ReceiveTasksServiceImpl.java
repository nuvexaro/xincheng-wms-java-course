package org.jeecg.modules.wms.inorder.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.modules.wms.config.WarehouseDictEnum;
import org.jeecg.modules.wms.inorder.entity.WmsStockInOrderItems;
import org.jeecg.modules.wms.inorder.entity.WmsStockInOrders;
import org.jeecg.modules.wms.inorder.service.IPutawayTasksService;
import org.jeecg.modules.wms.inorder.service.IReceiveTasksService;
import org.jeecg.modules.wms.inorder.service.IWmsStockInOrderItemsService;
import org.jeecg.modules.wms.inorder.service.IWmsStockInOrdersService;
import org.jeecg.modules.wms.inventory.service.impl.WmsInventoryTransByReceiving;
import org.jeecg.modules.wms.inventory.vo.WmsInventoryTransParam;
import org.jeecg.modules.wms.warehouse.entity.WmsStorageLocations;
import org.jeecg.modules.wms.warehouse.service.IWmsStorageLocationsService;
import org.jeecg.modules.wms.wmstask.entity.WmsTasks;
import org.jeecg.modules.wms.wmstask.entity.WmsTasksRecords;
import org.jeecg.modules.wms.wmstask.service.IWmsTasksService;
import org.jeecg.modules.wms.wmstask.util.TaskNumberUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @Description: 收货业务(创建收货任务、收货)
 * @Version: V1.0
 */
@Slf4j
@Service
public class ReceiveTasksServiceImpl implements IReceiveTasksService {

    /** 任务号前缀 */
    private static final String TASK_NUMBER_PREFIX = "TSK";
    /** 储位"是否可售"的取值: 1-可售(数据字典 yn: 1是 0否) */
    private static final String LOCATION_SELLABLE = "1";

    @Autowired
    private IWmsStockInOrdersService wmsStockInOrdersService;
    @Autowired
    private IWmsStockInOrderItemsService wmsStockInOrderItemsService;
    @Autowired
    private IWmsTasksService wmsTasksService;
    @Autowired
    private IWmsStorageLocationsService wmsStorageLocationsService;
    @Autowired
    private TaskNumberUtil taskNumberUtil;
    @Autowired
    private IPutawayTasksService putawayTasksService;
    /** 收货时的库存变更(库存变更接口有多个实现类, 这里要按具体的实现类注入) */
    @Autowired
    private WmsInventoryTransByReceiving wmsInventoryTransByReceiving;

    // ============================== 创建收货任务 ==============================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createReceiveTasks(String orderIds, String operator) {

        //1. 必填字段非空校验(前端虽然标了必填, 后端也要校验)
        if (oConvertUtils.isEmpty(orderIds)) {
            throw new JeecgBootException("请选择需要创建收货任务的入库单");
        }
        if (oConvertUtils.isEmpty(operator)) {
            throw new JeecgBootException("请选择执行人");
        }

        //2. 入库单id 是逗号分割的: 切割 -> 去空格 -> 去空串 -> 去重(保持勾选顺序)
        Set<String> orderIdSet = new LinkedHashSet<>();
        for (String raw : orderIds.split(",")) {
            String id = raw.trim();
            if (oConvertUtils.isNotEmpty(id)) {
                orderIdSet.add(id);
            }
        }
        if (orderIdSet.isEmpty()) {
            throw new JeecgBootException("请选择需要创建收货任务的入库单");
        }

        //3. 先把所有入库单校验一遍(这一步不写库、不取任务号)
        //   只要有一个不符合就直接报错, 避免白白消耗任务号
        List<WmsStockInOrders> orderList = new ArrayList<>();
        Map<String, List<WmsStockInOrderItems>> itemMap = new HashMap<>();
        for (String orderId : orderIdSet) {
            //3.1 入库单非空判断
            WmsStockInOrders order = wmsStockInOrdersService.getById(orderId);
            if (order == null) {
                throw new JeecgBootException("入库单不存在: " + orderId);
            }
            //3.2 状态判断: 只有"审核通过"的入库单才能创建收货任务
            if (!WarehouseDictEnum.INBOUND_APPROVED.getCode().equals(order.getStatus())) {
                throw new JeecgBootException("入库单" + order.getOrderNumber() + "不是审核通过状态, 不能创建收货任务");
            }
            //3.3 明细非空判断
            List<WmsStockInOrderItems> itemList = wmsStockInOrderItemsService.selectByMainId(orderId);
            if (itemList == null || itemList.isEmpty()) {
                throw new JeecgBootException("入库单" + order.getOrderNumber() + "没有入库明细, 不能创建收货任务");
            }
            orderList.add(order);
            itemMap.put(order.getId(), itemList);
        }

        //4. 全部校验通过后, 逐个入库单写入
        for (WmsStockInOrders order : orderList) {

            //4.1 更新入库单状态为"收货中"
            //    where 条件带上旧状态"审核通过": 并发时(连点两次/两人同时操作)只有一个请求能更新成功,
            //    另一个影响行数为 0, 直接报错回滚, 不会重复创建任务
            WmsStockInOrders orderUpdate = new WmsStockInOrders();
            orderUpdate.setStatus(WarehouseDictEnum.INBOUND_RECEIVING.getCode());
            boolean updated = wmsStockInOrdersService.update(orderUpdate,
                    new LambdaUpdateWrapper<WmsStockInOrders>()
                            .eq(WmsStockInOrders::getId, order.getId())
                            .eq(WmsStockInOrders::getStatus, WarehouseDictEnum.INBOUND_APPROVED.getCode()));
            if (!updated) {
                throw new JeecgBootException("入库单" + order.getOrderNumber() + "状态已变更, 请刷新后重试");
            }

            //4.2 更新该入库单下所有明细状态为"收货中"(一条 SQL 更新整单明细)
            WmsStockInOrderItems itemUpdate = new WmsStockInOrderItems();
            itemUpdate.setStatus(WarehouseDictEnum.INBOUND_DETAIL_RECEIVING.getCode());
            wmsStockInOrderItemsService.update(itemUpdate,
                    new LambdaUpdateWrapper<WmsStockInOrderItems>()
                            .eq(WmsStockInOrderItems::getOrderId, order.getId()));

            //4.3 根据入库单明细创建收货任务: 有几条明细就创建几条任务
            List<WmsTasks> taskList = new ArrayList<>();
            for (WmsStockInOrderItems item : itemMap.get(order.getId())) {
                WmsTasks task = new WmsTasks();
                //任务号: TSK + 年月日 + 5位序号(redis 生成)
                task.setTaskNumber(taskNumberUtil.generate(TASK_NUMBER_PREFIX));
                //任务类型: 收货任务
                task.setTaskType(WarehouseDictEnum.TASK_TYPE_RECEIVING.getCode());
                //任务状态: 已创建
                task.setTaskStatus(WarehouseDictEnum.TASK_STATUS_CREATED.getCode());
                //商品
                task.setProductId(item.getProductId());
                //数量 = 明细的采购数量
                task.setQuantity(item.getExpectedQuantity());
                //完成数量初始为 0 (completed_quantity 是 int 列, 对应实体的 Integer)
                task.setCompletedQuantity(0);
                //执行人
                task.setOperator(operator);
                //来源单据: 入库单 + 入库明细
                task.setStockInOrderId(order.getId());
                task.setStockInOrderItemId(item.getId());
                //目的仓库 = 入库单的仓库
                task.setTargetWarehouseId(order.getWarehouseId());
                taskList.add(task);
            }
            //批量添加收货任务
            wmsTasksService.saveBatch(taskList);
        }
    }

    // ============================== 收货 ==============================

    /**
     * 收货
     * 一个收货任务对应一条入库明细, 一条明细可以分多次(多个批次)收货, 所以一个收货任务可以收货多次
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void receive(WmsTasksRecords wmsTasksRecords) {
        //1. 参数校验(这一步不访问数据库)
        if (wmsTasksRecords == null || oConvertUtils.isEmpty(wmsTasksRecords.getTaskId())) {
            throw new JeecgBootException("请选择要收货的任务");
        }
        Integer execQuantity = wmsTasksRecords.getExecQuantity();
        if (execQuantity == null || execQuantity <= 0) {
            throw new JeecgBootException("收货数量必须大于0");
        }
        String inventoryAttribute = wmsTasksRecords.getInventoryAttribute();
        if (!WarehouseDictEnum.RECEIVING_GOOD.getCode().equals(inventoryAttribute)
                && !WarehouseDictEnum.RECEIVING_DEFECTIVE.getCode().equals(inventoryAttribute)) {
            throw new JeecgBootException("请选择库存属性: 良品 或 不良品");
        }
        if (oConvertUtils.isEmpty(wmsTasksRecords.getTargetLocationCode())) {
            throw new JeecgBootException("请选择储位");
        }

        //2. 加锁: 先锁收货任务, 再锁入库单
        //   注意: 这两条 select ... for update 必须是本事务里最先执行的两条 SQL, 前面不要加别的查询。
        //   这样同一个入库单的多次收货会排队执行, 后面查到的收货记录、明细数量都是最新的, 汇总数量不会算错
        WmsTasks task = wmsTasksService.getByIdForUpdate(wmsTasksRecords.getTaskId());
        if (task == null) {
            throw new JeecgBootException("收货任务不存在");
        }
        if (!WarehouseDictEnum.TASK_TYPE_RECEIVING.getCode().equals(task.getTaskType())) {
            throw new JeecgBootException("任务" + task.getTaskNumber() + "不是收货任务");
        }
        WmsStockInOrders order = wmsStockInOrdersService.getByIdForUpdate(task.getStockInOrderId());
        if (order == null) {
            throw new JeecgBootException("收货任务" + task.getTaskNumber() + "对应的入库单不存在");
        }
        if (!WarehouseDictEnum.INBOUND_RECEIVING.getCode().equals(order.getStatus())) {
            throw new JeecgBootException("入库单" + order.getOrderNumber() + "不是收货中状态, 不能收货");
        }

        //3. 储位校验
        //3.1 储位必须是该任务目的仓库下的储位
        List<WmsStorageLocations> locationList = wmsStorageLocationsService.list(new LambdaQueryWrapper<WmsStorageLocations>()
                .eq(WmsStorageLocations::getLocationCode, wmsTasksRecords.getTargetLocationCode())
                .eq(WmsStorageLocations::getWarehouseId, task.getTargetWarehouseId()));
        if (locationList == null || locationList.isEmpty()) {
            throw new JeecgBootException("储位" + wmsTasksRecords.getTargetLocationCode() + "不存在, 或者不属于该任务的仓库");
        }
        //3.2 不良品不允许放在可售储位
        WmsStorageLocations location = locationList.get(0);
        if (WarehouseDictEnum.RECEIVING_DEFECTIVE.getCode().equals(inventoryAttribute)
                && LOCATION_SELLABLE.equals(location.getIsSellable())) {
            throw new JeecgBootException("不良品不允许放在可售储位, 请选择不可售的储位");
        }

        //4. 前端填写的"收货日期"作为执行时间; 收货没有来源储位
        if (wmsTasksRecords.getReceivingDate() != null) {
            wmsTasksRecords.setOperationTime(wmsTasksRecords.getReceivingDate());
        }
        wmsTasksRecords.setSourceLocationCode(null);

        //5. 执行任务: 添加收货记录, 累加任务的完成数量, 收完时任务状态变为已完成; 返回最新任务数据
        WmsTasks latestTask = wmsTasksService.execute(wmsTasksRecords);

        //5.1 把这次收货的目的储位、批号、保质期更新到收货任务上(任务上记的是最近一次收货的信息)
        WmsTasks taskUpdate = new WmsTasks();
        taskUpdate.setId(latestTask.getId());
        taskUpdate.setTargetLocationCode(wmsTasksRecords.getTargetLocationCode());
        taskUpdate.setBatchNumber(wmsTasksRecords.getBatchNumber());
        taskUpdate.setExpiryDate(wmsTasksRecords.getExpiryDate());
        wmsTasksService.updateById(taskUpdate);

        //6. 更新入库单明细的 收货数量(良品)、不良品数量、状态
        wmsStockInOrderItemsService.updateReceivedStatus(latestTask.getStockInOrderItemId());

        //7. 更新入库单的 实际收货总量、不良品总数量、状态
        boolean isCompleted = wmsStockInOrdersService.updateReceivedStatus(latestTask.getStockInOrderId());

        //8. 存储库存: 良品增加在库数量和可用数量, 不良品只增加在库数量
        wmsInventoryTransByReceiving.transfer(createWmsInventoryTransParam(wmsTasksRecords));

        //9. 入库单收货完成后, 根据收货记录自动创建上架任务
        if (isCompleted) {
            log.info("入库单{}已全部收货完成, 开始创建上架任务", order.getOrderNumber());
            putawayTasksService.createPutawayTasks(latestTask.getStockInOrderId());
        }
    }

    /**
     * 根据收货记录创建库存变更参数对象
     * (执行任务后, 收货记录里的商品id、仓库id、任务号、执行人、执行时间都已经按任务表填好了)
     */
    private WmsInventoryTransParam createWmsInventoryTransParam(WmsTasksRecords wmsTasksRecords) {
        WmsInventoryTransParam param = new WmsInventoryTransParam();
        // 商品id
        param.setProductId(wmsTasksRecords.getProductId());
        // 执行数量
        param.setExecQuantity(wmsTasksRecords.getExecQuantity());
        // 仓库id
        param.setWarehouseId(wmsTasksRecords.getTargetWarehouseId());
        // 目的储位编码(收货放的储位)
        param.setTargetLocationCode(wmsTasksRecords.getTargetLocationCode());
        // 批次号
        param.setBatchNumber(wmsTasksRecords.getBatchNumber());
        // 保质期
        param.setExpiryDate(wmsTasksRecords.getExpiryDate());
        // 是否可售: 良品可售, 不良品不可售
        if (WarehouseDictEnum.INVENTORY_ATTRIBUTE_GOOD.getCode().equals(wmsTasksRecords.getInventoryAttribute())) {
            param.setIsSellable(WarehouseDictEnum.INVENTORY_SELLABLE.getCode());
        } else {
            param.setIsSellable(WarehouseDictEnum.INVENTORY_NOTSELLABLE.getCode());
        }
        // 变更类型: 收货
        param.setTransactionType(WarehouseDictEnum.INVENTORY_RECEIVING.getCode());
        // 执行人
        param.setOperator(wmsTasksRecords.getOperator());
        // 执行时间
        param.setOperationTime(wmsTasksRecords.getOperationTime());
        // 备注: 关联的任务号
        param.setRemarks(wmsTasksRecords.getTaskNumber());
        return param;
    }
}
