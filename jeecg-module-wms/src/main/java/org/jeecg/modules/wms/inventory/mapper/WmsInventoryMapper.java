package org.jeecg.modules.wms.inventory.mapper;

import java.util.Date;
import java.util.List;

import org.apache.ibatis.annotations.Param;
import org.jeecg.modules.wms.inventory.entity.WmsInventory;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * @Description: 库存表
 * @Author: jeecg-boot
 * @Date:   2026-10-06
 * @Version: V1.0
 */
public interface WmsInventoryMapper extends BaseMapper<WmsInventory> {

    /**
     * 增加库存: 在库数量 + quantity, 可用数量 + availableQuantity
     * 用 "数量 = 数量 + ?" 的写法在数据库里累加, 并发时不会丢数据
     *
     * @return 影响行数
     */
    int increaseStock(@Param("id") String id, @Param("quantity") Integer quantity,
                      @Param("availableQuantity") Integer availableQuantity, @Param("now") Date now);

    /**
     * 扣减库存: 在库数量、可用数量同时减 quantity; 库存不够时不扣减
     *
     * @return 影响行数, 0 表示在库数量或可用数量不足
     */
    int decreaseStock(@Param("id") String id, @Param("quantity") Integer quantity, @Param("now") Date now);

    /**
     * 分页查询库存(内连接 商品、货主、仓库、储位、储区)
     *
     * @param page 分页参数
     * @param inv  查询条件
     * @param ids  只查这些库存id(页面上勾选了行时使用), 可以为 null
     * @return 库存分页数据
     */
    IPage<WmsInventory> selectPageList(Page<WmsInventory> page, @Param("inv") WmsInventory inv, @Param("ids") List<String> ids);

    /**
     * 查询要导出的库存(不分页, 最多导出 10000 条)
     *
     * @param inv 查询条件
     * @param ids 只查这些库存id(页面上勾选了行时使用), 可以为 null
     * @return 库存列表
     */
    List<WmsInventory> selectExportList(@Param("inv") WmsInventory inv, @Param("ids") List<String> ids);

    /**
     * 查询可以分配给出库单的库存: 指定商品、指定仓库、可售、可用数量大于 0; 指定了批号时只查该批号
     * 排序就是分配的先后顺序: 先到期先出(没有保质期的排最后), 到期日相同的先进先出
     */
    List<WmsInventory> selectAllocatableList(@Param("productId") String productId, @Param("warehouseId") String warehouseId,
                                             @Param("batchNumber") String batchNumber);

    /**
     * 锁定库存(分配库存): 可用数量减少, 分配数量增加; 可用数量不够时不锁定
     *
     * @return 影响行数, 0 表示可用数量不足
     */
    int lockStock(@Param("id") String id, @Param("quantity") Integer quantity, @Param("now") Date now);
}
