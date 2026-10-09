package org.jeecg.modules.wms.inventory.service;

import org.jeecg.modules.wms.config.ExcelImportResult;
import org.jeecg.modules.wms.inventory.entity.WmsInventory;
import org.jeecg.modules.wms.inventory.excel.WmsInventoryImport;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import java.util.List;

/**
 * @Description: 库存表
 * @Author: jeecg-boot
 * @Date:   2026-10-06
 * @Version: V1.0
 */
public interface IWmsInventoryService extends IService<WmsInventory> {

    /**
     * 根据唯一键(商品id + 储位编码 + 批号)获取库存
     *
     * @param productId    商品id
     * @param locationCode 储位编码
     * @param batchNumber  批号, 没有批号时传 null 或空字符串都可以
     * @return 库存, 不存在时返回 null
     */
    WmsInventory getInventoryByUniqueKey(String productId, String locationCode, String batchNumber);

    /**
     * 增加库存
     *
     * @param id                库存id
     * @param quantity          在库数量增加多少
     * @param availableQuantity 可用数量增加多少(不良品传 0)
     * @return 是否成功
     */
    boolean increaseStock(String id, int quantity, int availableQuantity);

    /**
     * 扣减库存: 在库数量、可用数量同时扣减
     *
     * @param id       库存id
     * @param quantity 扣减数量
     * @return 是否成功, false 表示在库数量或可用数量不足
     */
    boolean decreaseStock(String id, int quantity);

    /**
     * 分页查询库存(带仓库名称、货主、商品、储位类型、储区类型)
     *
     * @param page         分页参数
     * @param wmsInventory 查询条件
     * @return 库存分页数据
     */
    IPage<WmsInventory> pageList(Page<WmsInventory> page, WmsInventory wmsInventory);

    /**
     * 查询要导出的库存(不分页, 最多 10000 条)
     *
     * @param wmsInventory 查询条件
     * @param ids          只导出这些库存id, 为空表示按查询条件导出全部
     * @return 库存列表
     */
    List<WmsInventory> exportList(WmsInventory wmsInventory, List<String> ids);

    /**
     * 导入库存(用于初始化库存): 库存已存在(商品 + 储位 + 批号 相同)则跳过, 不存在则新增
     *
     * @param cachedDataList 从 Excel 读到的一批库存
     * @param result         导入结果, 成功/跳过/失败的条数和原因会累加到这个对象里
     */
    void importInventory(List<WmsInventoryImport> cachedDataList, ExcelImportResult result);

    /**
     * 查询可以分配给出库单的库存, 返回的顺序就是分配的先后顺序(先到期先出, 其次先进先出)
     *
     * @param productId   商品id
     * @param warehouseId 仓库id
     * @param batchNumber 指定批号, 没指定传 null
     */
    List<WmsInventory> listAllocatable(String productId, String warehouseId, String batchNumber);

    /**
     * 锁定库存: 可用数量减少, 分配数量增加
     *
     * @return false 表示可用数量不足
     */
    boolean lockStock(String id, int quantity);
}
