package org.jeecg.modules.wms.inventory.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.PageDTO;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.modules.wms.inventory.entity.WmsInventory;
import org.jeecg.modules.wms.inventory.mapper.WmsInventoryMapper;
import org.jeecg.modules.wms.inventory.service.IWmsInventoryService;
import org.jeecg.modules.wms.outorder.entity.WmsOutOrdersItems;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import java.util.List;

/**
 * @Description: 库存表
 * @Author: jeecg-boot
 * @Date:   2025-08-30
 * @Version: V1.0
 */
@Service
public class WmsInventoryServiceImpl extends ServiceImpl<WmsInventoryMapper, WmsInventory> implements IWmsInventoryService {

    @Override
    public WmsInventory getInventoryByUniqueKey(String productId, String locationCode, String batchNumber) {
        LambdaQueryWrapper<WmsInventory> eq = new LambdaQueryWrapper<WmsInventory>()
                .eq(WmsInventory::getProductId, productId)
                .eq(WmsInventory::getLocationCode, locationCode)
                .eq(StringUtils.isNotEmpty(batchNumber),WmsInventory::getBatchNumber, batchNumber)
                .eq(StringUtils.isEmpty(batchNumber),WmsInventory::getBatchNumber,"");
        WmsInventory inventory = this.getOne(eq);

        return inventory;
    }

    @Override
    public IPage<WmsInventory> queryList(WmsInventory wmsInventory, Integer pageNo, Integer pageSize) {
        //开启分页查询,当执行查询时，插件进行相关的sql拦截进行分页操作，返回一个page对象
        Page<WmsInventory> page = PageHelper.startPage(pageNo, pageSize);
        List<WmsInventory> list = baseMapper.queryList(wmsInventory);
        PageDTO<WmsInventory> wmsInventoryPageDTO = new PageDTO<>();
        wmsInventoryPageDTO.setRecords(list);
        wmsInventoryPageDTO.setTotal(page.getTotal());
        wmsInventoryPageDTO.setSize(page.getPageSize());
        wmsInventoryPageDTO.setCurrent(page.getPageNum());
        wmsInventoryPageDTO.setPages(page.getPages());
        return wmsInventoryPageDTO;
    }

    /**
     * 查询可用库存(课件 3.2)
     * 对应的 SQL:
     *   SELECT * FROM wms_inventory
     *   WHERE is_sellable = '1' AND product_id = #{skuId} AND available_quantity > 0
     *     AND warehouse_id = #{warehouseId}
     *     [AND batch_number = #{batchNo}]            -- 出库明细指定了批次号时才有这个条件
     *   ORDER BY expiry_date ASC, stock_in_time ASC  -- 先到期先出, 到期日相同的先进先出
     */
    @Override
    public List<WmsInventory> selectAvailableBySku(String warehouseId, WmsOutOrdersItems skuItem) {
        //使用MybatisPlus的查询条件
        LambdaQueryWrapper<WmsInventory> queryWrapper = new LambdaQueryWrapper<>();
        //商品
        queryWrapper.eq(WmsInventory::getProductId, skuItem.getSkuId());
        //仓库id
        queryWrapper.eq(WmsInventory::getWarehouseId, warehouseId);
        //只分配可售库存(需求 3.5.4: 库存必须为可售库存)
        queryWrapper.eq(WmsInventory::getIsSellable, "1");
        //可用数量大于0
        queryWrapper.gt(WmsInventory::getAvailableQuantity, 0);
        //如果有批次号, 是针对某批次的商品进行出库, 只查该批次的库存;
        //没有批次号时不限制批次, 按下面的排序先到期先出(需求 3.5.4 的两种分配策略)
        //注意: 课件原代码是 没有批次号时只查批次号为空的库存, 这样带批次的库存永远分配不到, 所以这里做了调整
        if (StringUtils.isNotBlank(skuItem.getBatchNumber())) {
            queryWrapper.eq(WmsInventory::getBatchNumber, skuItem.getBatchNumber());
        }
        //保质期到期日升序, 入库时间升序; 没有保质期的库存排在最后
        queryWrapper.last("ORDER BY expiry_date IS NULL, expiry_date ASC, stock_in_time ASC");
        return baseMapper.selectList(queryWrapper);
    }
}
