package org.jeecg.modules.wms.inventory.service.impl;

import java.util.Date;

import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.modules.wms.config.WarehouseDictEnum;
import org.jeecg.modules.wms.inventory.entity.WmsInventory;
import org.jeecg.modules.wms.inventory.entity.WmsInventoryTrans;
import org.jeecg.modules.wms.inventory.mapper.WmsInventoryTransMapper;
import org.jeecg.modules.wms.inventory.service.IWmsInventoryService;
import org.jeecg.modules.wms.inventory.service.IWmsInventoryTransService;
import org.jeecg.modules.wms.inventory.vo.WmsInventoryTransParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

/**
 * @Description: 上架时的库存变更(只针对良品): 把库存从收货储位移到上架储位
 *               原储位: 在库数量减少, 可用数量减少, 分配数量不动
 *               新储位: 在库数量增加, 可用数量增加, 分配数量不动
 * @Version: V1.0
 */
@Service
public class WmsInventoryTransByPutaway extends ServiceImpl<WmsInventoryTransMapper, WmsInventoryTrans> implements IWmsInventoryTransService {

    @Autowired
    private IWmsInventoryService wmsInventoryService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void transfer(WmsInventoryTransParam inventoryTransParam) {
        //1. 非空判断: 来源储位、目标储位、商品id、执行数量
        if (inventoryTransParam == null
                || oConvertUtils.isEmpty(inventoryTransParam.getSourceLocationCode())
                || oConvertUtils.isEmpty(inventoryTransParam.getTargetLocationCode())
                || oConvertUtils.isEmpty(inventoryTransParam.getProductId())
                || inventoryTransParam.getExecQuantity() == null) {
            throw new JeecgBootException("上架变更库存失败: 来源储位、目标储位、商品id、执行数量不能为空");
        }
        int execQuantity = inventoryTransParam.getExecQuantity();
        if (execQuantity <= 0) {
            throw new JeecgBootException("上架变更库存失败: 执行数量必须大于0");
        }
        String sourceLocationCode = inventoryTransParam.getSourceLocationCode();
        String targetLocationCode = inventoryTransParam.getTargetLocationCode();
        if (sourceLocationCode.equals(targetLocationCode)) {
            throw new JeecgBootException("上架的目的储位不能和来源储位相同");
        }
        String batchNumber = oConvertUtils.isEmpty(inventoryTransParam.getBatchNumber()) ? "" : inventoryTransParam.getBatchNumber().trim();
        Date operationTime = inventoryTransParam.getOperationTime() == null ? new Date() : inventoryTransParam.getOperationTime();

        //2. 原储位: 扣减在库数量、可用数量
        WmsInventory source = wmsInventoryService.getInventoryByUniqueKey(inventoryTransParam.getProductId(), sourceLocationCode, batchNumber);
        if (source == null) {
            throw new JeecgBootException("来源储位" + sourceLocationCode + "没有该商品的库存, 不能上架");
        }
        boolean decreased = wmsInventoryService.decreaseStock(source.getId(), execQuantity);
        if (!decreased) {
            throw new JeecgBootException("来源储位" + sourceLocationCode + "的可用库存不足, 不能上架");
        }

        //3. 新储位: 根据唯一键(商品id + 储位 + 批号)查询库存, 不存在则新增, 存在则累加
        WmsInventory target = wmsInventoryService.getInventoryByUniqueKey(inventoryTransParam.getProductId(), targetLocationCode, batchNumber);
        if (target == null) {
            WmsInventory inventory = new WmsInventory();
            inventory.setProductId(inventoryTransParam.getProductId());
            inventory.setLocationCode(targetLocationCode);
            inventory.setBatchNumber(batchNumber);
            inventory.setWarehouseId(oConvertUtils.isEmpty(inventoryTransParam.getWarehouseId()) ? source.getWarehouseId() : inventoryTransParam.getWarehouseId());
            // 货主、保质期、入库时间沿用原库存的(入库时间不变, 以后出库做先进先出时才准)
            inventory.setOwnerId(source.getOwnerId());
            inventory.setExpiryDate(source.getExpiryDate());
            inventory.setStockInTime(source.getStockInTime() == null ? operationTime : source.getStockInTime());
            inventory.setStockQuantity(execQuantity);
            inventory.setAvailableQuantity(execQuantity);
            inventory.setAllocatedQuantity(0);
            // 上架的都是良品, 可售
            inventory.setIsSellable(WarehouseDictEnum.INVENTORY_SELLABLE.getCode());
            wmsInventoryService.save(inventory);
        } else {
            boolean increased = wmsInventoryService.increaseStock(target.getId(), execQuantity, execQuantity);
            if (!increased) {
                throw new JeecgBootException("库存更新失败");
            }
        }

        //4. 添加两条库存变更记录: 原储位减少(负数), 新储位增加(正数)
        saveTrans(inventoryTransParam, sourceLocationCode, -execQuantity, batchNumber, operationTime);
        saveTrans(inventoryTransParam, targetLocationCode, execQuantity, batchNumber, operationTime);
    }

    private void saveTrans(WmsInventoryTransParam param, String locationCode, int changeQuantity, String batchNumber, Date operationTime) {
        WmsInventoryTrans trans = new WmsInventoryTrans();
        trans.setProductId(param.getProductId());
        trans.setLocationCode(locationCode);
        trans.setChangeQuantity(changeQuantity);
        trans.setTransactionType(param.getTransactionType());
        trans.setReferenceNumber(param.getRemarks());
        trans.setRemarks(param.getRemarks());
        trans.setTransactionTime(operationTime);
        trans.setBatchNumber(batchNumber);
        save(trans);
    }
}
