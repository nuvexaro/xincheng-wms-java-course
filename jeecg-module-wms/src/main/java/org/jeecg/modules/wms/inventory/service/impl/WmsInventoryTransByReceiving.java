package org.jeecg.modules.wms.inventory.service.impl;

import java.util.Date;

import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.modules.wms.config.WarehouseDictEnum;
import org.jeecg.modules.wms.goods.entity.WmsProducts;
import org.jeecg.modules.wms.goods.service.IWmsProductsService;
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
 * @Description: 收货时的库存变更
 *               良品:   在库数量增加, 可用数量增加, 分配数量不动
 *               不良品: 在库数量增加, 可用数量不动, 分配数量不动
 * @Version: V1.0
 */
@Service
public class WmsInventoryTransByReceiving extends ServiceImpl<WmsInventoryTransMapper, WmsInventoryTrans> implements IWmsInventoryTransService {

    @Autowired
    private IWmsInventoryService wmsInventoryService;
    @Autowired
    private IWmsProductsService wmsProductsService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void transfer(WmsInventoryTransParam inventoryTransParam) {
        //1. 非空判断: 目标储位、商品id、执行数量、是否可售
        if (inventoryTransParam == null
                || oConvertUtils.isEmpty(inventoryTransParam.getTargetLocationCode())
                || oConvertUtils.isEmpty(inventoryTransParam.getProductId())
                || inventoryTransParam.getExecQuantity() == null
                || oConvertUtils.isEmpty(inventoryTransParam.getIsSellable())) {
            throw new JeecgBootException("存储库存失败: 目标储位、商品id、执行数量、是否可售不能为空");
        }
        int execQuantity = inventoryTransParam.getExecQuantity();
        if (execQuantity <= 0) {
            throw new JeecgBootException("存储库存失败: 执行数量必须大于0");
        }

        //2. 查询商品信息(库存里要存货主)
        WmsProducts products = wmsProductsService.getById(inventoryTransParam.getProductId());
        if (products == null) {
            throw new JeecgBootException("存储库存失败: 商品不存在");
        }

        //3. 可售(良品)才增加可用数量
        boolean sellable = WarehouseDictEnum.INVENTORY_SELLABLE.getCode().equals(inventoryTransParam.getIsSellable());
        int availableQuantity = sellable ? execQuantity : 0;
        // 库存表的批号不允许为 null, 没有批号时存空字符串
        String batchNumber = oConvertUtils.isEmpty(inventoryTransParam.getBatchNumber()) ? "" : inventoryTransParam.getBatchNumber().trim();
        Date operationTime = inventoryTransParam.getOperationTime() == null ? new Date() : inventoryTransParam.getOperationTime();

        //4. 根据唯一键(商品id + 储位 + 批号)查询库存: 不存在则新增, 存在则累加
        WmsInventory wmsInventoryDb = wmsInventoryService.getInventoryByUniqueKey(
                inventoryTransParam.getProductId(), inventoryTransParam.getTargetLocationCode(), batchNumber);
        if (wmsInventoryDb == null) {
            WmsInventory inventory = new WmsInventory();
            inventory.setProductId(inventoryTransParam.getProductId());
            inventory.setLocationCode(inventoryTransParam.getTargetLocationCode());
            inventory.setBatchNumber(batchNumber);
            inventory.setWarehouseId(inventoryTransParam.getWarehouseId());
            inventory.setOwnerId(products.getOwnerId());
            // 在库数量
            inventory.setStockQuantity(execQuantity);
            // 可用数量
            inventory.setAvailableQuantity(availableQuantity);
            // 分配数量
            inventory.setAllocatedQuantity(0);
            // 是否可售
            inventory.setIsSellable(inventoryTransParam.getIsSellable());
            // 保质期
            inventory.setExpiryDate(inventoryTransParam.getExpiryDate());
            // 入库时间
            inventory.setStockInTime(operationTime);
            wmsInventoryService.save(inventory);
        } else {
            boolean updated = wmsInventoryService.increaseStock(wmsInventoryDb.getId(), execQuantity, availableQuantity);
            if (!updated) {
                throw new JeecgBootException("库存更新失败");
            }
        }

        //5. 添加库存变更记录
        WmsInventoryTrans trans = new WmsInventoryTrans();
        trans.setProductId(inventoryTransParam.getProductId());
        trans.setLocationCode(inventoryTransParam.getTargetLocationCode());
        trans.setChangeQuantity(execQuantity);
        trans.setTransactionType(inventoryTransParam.getTransactionType());
        trans.setReferenceNumber(inventoryTransParam.getRemarks());
        trans.setRemarks(inventoryTransParam.getRemarks());
        trans.setTransactionTime(operationTime);
        trans.setBatchNumber(batchNumber);
        save(trans);
    }
}
