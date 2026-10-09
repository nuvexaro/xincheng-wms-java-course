package org.jeecg.modules.wms.inventory.service.impl;

import java.util.Date;
import java.util.List;

import org.jeecg.common.util.oConvertUtils;
import org.jeecg.modules.wms.config.ExcelImportResult;
import org.jeecg.modules.wms.config.WarehouseDictEnum;
import org.jeecg.modules.wms.goods.entity.WmsCargoOwners;
import org.jeecg.modules.wms.goods.entity.WmsProducts;
import org.jeecg.modules.wms.goods.service.IWmsCargoOwnersService;
import org.jeecg.modules.wms.goods.service.IWmsProductsService;
import org.jeecg.modules.wms.inventory.entity.WmsInventory;
import org.jeecg.modules.wms.inventory.entity.WmsInventoryTrans;
import org.jeecg.modules.wms.inventory.excel.WmsInventoryImport;
import org.jeecg.modules.wms.inventory.mapper.WmsInventoryTransMapper;
import org.jeecg.modules.wms.warehouse.entity.WmsStorageLocations;
import org.jeecg.modules.wms.warehouse.entity.WmsWarehouses;
import org.jeecg.modules.wms.warehouse.service.IWmsStorageLocationsService;
import org.jeecg.modules.wms.warehouse.service.IWmsWarehousesService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.jeecg.modules.wms.inventory.mapper.WmsInventoryMapper;
import org.jeecg.modules.wms.inventory.service.IWmsInventoryService;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

/**
 * @Description: 库存表
 * @Author: jeecg-boot
 * @Date:   2026-10-06
 * @Version: V1.0
 */
@Service
public class WmsInventoryServiceImpl extends ServiceImpl<WmsInventoryMapper, WmsInventory> implements IWmsInventoryService {

    @Autowired
    private IWmsWarehousesService wmsWarehousesService;
    @Autowired
    private IWmsCargoOwnersService wmsCargoOwnersService;
    @Autowired
    private IWmsProductsService wmsProductsService;
    @Autowired
    private IWmsStorageLocationsService wmsStorageLocationsService;
    @Autowired
    private WmsInventoryTransMapper wmsInventoryTransMapper;

    @Override
    public WmsInventory getInventoryByUniqueKey(String productId, String locationCode, String batchNumber) {
        // 库存表的批号不允许为 null, 没有批号时存的是空字符串
        return baseMapper.selectOne(new LambdaQueryWrapper<WmsInventory>()
                .eq(WmsInventory::getProductId, productId)
                .eq(WmsInventory::getLocationCode, locationCode)
                .eq(WmsInventory::getBatchNumber, batchNumber == null ? "" : batchNumber));
    }

    @Override
    public boolean increaseStock(String id, int quantity, int availableQuantity) {
        return baseMapper.increaseStock(id, quantity, availableQuantity, new Date()) > 0;
    }

    @Override
    public boolean decreaseStock(String id, int quantity) {
        return baseMapper.decreaseStock(id, quantity, new Date()) > 0;
    }

    @Override
    public IPage<WmsInventory> pageList(Page<WmsInventory> page, WmsInventory wmsInventory) {
        // 查询条件对象不能为 null, 否则 xml 里的 inv.xxx 会报错
        if (wmsInventory == null) {
            wmsInventory = new WmsInventory();
        }
        return baseMapper.selectPageList(page, wmsInventory, null);
    }

    @Override
    public List<WmsInventory> exportList(WmsInventory wmsInventory, List<String> ids) {
        if (wmsInventory == null) {
            wmsInventory = new WmsInventory();
        }
        return baseMapper.selectExportList(wmsInventory, ids);
    }

    /**
     * 导入库存
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void importInventory(List<WmsInventoryImport> cachedDataList, ExcelImportResult result) {
        if (cachedDataList == null || cachedDataList.isEmpty()) {
            return;
        }
        Date now = new Date();
        // 逐条处理: 有问题的行记下原因后跳过, 不影响其它行
        for (WmsInventoryImport imp : cachedDataList) {
            //1. 必填校验: 仓库名称、货主编码、货主、商品编码、商品名称、储位编码、数量
            if (oConvertUtils.isEmpty(imp.getWarehouseName()) || oConvertUtils.isEmpty(imp.getOwnerCode())
                    || oConvertUtils.isEmpty(imp.getOwnerName()) || oConvertUtils.isEmpty(imp.getProductCode())
                    || oConvertUtils.isEmpty(imp.getProductName()) || oConvertUtils.isEmpty(imp.getLocationCode())
                    || imp.getQuantity() == null) {
                result.fail(imp.getRowNo(), "仓库名称、货主编码、货主、商品编码、商品名称、储位编码、数量不能为空");
                continue;
            }
            if (imp.getQuantity() <= 0) {
                result.fail(imp.getRowNo(), "数量必须大于0");
                continue;
            }

            //2. 仓库名称 -> 仓库
            List<WmsWarehouses> warehouseList = wmsWarehousesService.list(new LambdaQueryWrapper<WmsWarehouses>()
                    .eq(WmsWarehouses::getWarehouseName, imp.getWarehouseName().trim()));
            if (warehouseList == null || warehouseList.isEmpty()) {
                result.fail(imp.getRowNo(), "仓库" + imp.getWarehouseName() + "不存在");
                continue;
            }
            String warehouseId = warehouseList.get(0).getId();

            //3. 货主编码 -> 货主
            List<WmsCargoOwners> ownerList = wmsCargoOwnersService.list(new LambdaQueryWrapper<WmsCargoOwners>()
                    .eq(WmsCargoOwners::getOwnerCode, imp.getOwnerCode().trim()));
            if (ownerList == null || ownerList.isEmpty()) {
                result.fail(imp.getRowNo(), "货主编码" + imp.getOwnerCode() + "不存在");
                continue;
            }
            String ownerId = ownerList.get(0).getId();

            //4. 货主 + 商品编码 -> 商品(每个货主有自己的商品编码, 所以要带上货主一起查)
            List<WmsProducts> productList = wmsProductsService.list(new LambdaQueryWrapper<WmsProducts>()
                    .eq(WmsProducts::getOwnerId, ownerId)
                    .eq(WmsProducts::getProductCode, imp.getProductCode().trim()));
            if (productList == null || productList.isEmpty()) {
                result.fail(imp.getRowNo(), "货主" + imp.getOwnerName() + "下没有商品编码为" + imp.getProductCode() + "的商品");
                continue;
            }
            String productId = productList.get(0).getId();

            //5. 仓库 + 储位编码 -> 储位
            String locationCode = imp.getLocationCode().trim();
            List<WmsStorageLocations> locationList = wmsStorageLocationsService.list(new LambdaQueryWrapper<WmsStorageLocations>()
                    .eq(WmsStorageLocations::getWarehouseId, warehouseId)
                    .eq(WmsStorageLocations::getLocationCode, locationCode));
            if (locationList == null || locationList.isEmpty()) {
                result.fail(imp.getRowNo(), "仓库" + imp.getWarehouseName() + "下没有储位" + locationCode);
                continue;
            }
            WmsStorageLocations location = locationList.get(0);

            //6. 库存已存在则不导入(需求: 导入只用于初始化库存)
            String batchNumber = oConvertUtils.isEmpty(imp.getBatchNumber()) ? "" : imp.getBatchNumber().trim();
            if (getInventoryByUniqueKey(productId, locationCode, batchNumber) != null) {
                result.skip(imp.getRowNo(), "库存已存在");
                continue;
            }

            //7. 新增库存: 可售储位上的库存可用, 不可售储位上的库存可用数量为 0
            boolean sellable = WarehouseDictEnum.INVENTORY_SELLABLE.getCode().equals(location.getIsSellable());
            WmsInventory inventory = new WmsInventory();
            inventory.setProductId(productId);
            inventory.setLocationCode(locationCode);
            inventory.setBatchNumber(batchNumber);
            inventory.setWarehouseId(warehouseId);
            inventory.setOwnerId(ownerId);
            inventory.setStockQuantity(imp.getQuantity());
            inventory.setAvailableQuantity(sellable ? imp.getQuantity() : 0);
            inventory.setAllocatedQuantity(0);
            inventory.setIsSellable(sellable ? WarehouseDictEnum.INVENTORY_SELLABLE.getCode() : WarehouseDictEnum.INVENTORY_NOTSELLABLE.getCode());
            inventory.setExpiryDate(imp.getExpiryDate());
            inventory.setStockInTime(now);
            save(inventory);

            //8. 记录库存变更: 类型为"调整"
            WmsInventoryTrans trans = new WmsInventoryTrans();
            trans.setProductId(productId);
            trans.setLocationCode(locationCode);
            trans.setChangeQuantity(imp.getQuantity());
            trans.setTransactionType(WarehouseDictEnum.INVENTORY_ADJUSTMENT.getCode());
            trans.setRemarks("库存导入");
            trans.setTransactionTime(now);
            trans.setBatchNumber(batchNumber);
            wmsInventoryTransMapper.insert(trans);

            result.success();
        }
    }

    @Override
    public List<WmsInventory> listAllocatable(String productId, String warehouseId, String batchNumber) {
        return baseMapper.selectAllocatableList(productId, warehouseId, batchNumber);
    }

    @Override
    public boolean lockStock(String id, int quantity) {
        return baseMapper.lockStock(id, quantity, new Date()) > 0;
    }
}
