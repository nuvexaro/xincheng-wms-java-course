package org.jeecg.modules.wms.inventory.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import java.util.List;
import org.jeecg.modules.wms.inventory.entity.WmsInventory;
import org.jeecg.modules.wms.outorder.entity.WmsOutOrdersItems;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * @Description: 库存表
 * @Author: jeecg-boot
 * @Date:   2025-08-30
 * @Version: V1.0
 */
public interface IWmsInventoryService extends IService<WmsInventory> {
    /**
     * 查询唯一库存
     */
    WmsInventory getInventoryByUniqueKey(String productId, String locationCode, String batchNumber);

    /**
     * 综合查询库存列表
     */
    public IPage<WmsInventory> queryList(WmsInventory wmsInventory, Integer pageNo, Integer pageSize);

    /**
     * 查询可用库存(课件 3.2)
     * @param warehouseId 仓库id
     * @param skuItem 出库单明细(里面有商品id、批次号)
     * @return 可用库存列表, 列表的先后顺序就是分配的先后顺序
     */
    public List<WmsInventory> selectAvailableBySku(String warehouseId, WmsOutOrdersItems skuItem);
}
