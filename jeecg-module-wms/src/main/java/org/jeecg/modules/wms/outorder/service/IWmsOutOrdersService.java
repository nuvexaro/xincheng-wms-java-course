package org.jeecg.modules.wms.outorder.service;

import java.util.List;

import org.jeecg.modules.wms.outorder.entity.WmsOutOrders;
import org.jeecg.modules.wms.outorder.entity.WmsOutOrdersAllocation;
import org.jeecg.modules.wms.outorder.entity.WmsOutOrdersItems;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * @Description: 出库单主表
 * @Version: V1.0
 */
public interface IWmsOutOrdersService extends IService<WmsOutOrders> {

    /** 分页查询出库单(补全货主名称) */
    IPage<WmsOutOrders> pageList(Page<WmsOutOrders> page, Wrapper<WmsOutOrders> queryWrapper);

    /** 通过出库单id查询出库单明细(带商品名称) */
    List<WmsOutOrdersItems> selectItemsByMainId(String orderId);

    /** 通过出库单id查询库存分配明细(带商品名称) */
    List<WmsOutOrdersAllocation> selectAllocationByMainId(String orderId);

    /** 创建出库单: 只添加出库单主表, 自动生成出库单号, 状态为已创建 */
    void add(WmsOutOrders wmsOutOrders);

    /** 修改出库单: 出库单修改数据, 出库单明细先删除再添加 */
    void updateMain(WmsOutOrders wmsOutOrders, List<WmsOutOrdersItems> itemsList);

    /** 删除出库单: 已创建状态直接删除, 审核失败状态更新为已取消, 其它状态不允许删除 */
    void delMain(String id);

    /** 批量删除出库单, ids 多个以逗号分割 */
    void delBatchMain(String ids);

    /** 提交审核: 已创建、审核失败 -> 提交审核 */
    void submitAudit(String id);

    /** 审核: 提交审核 -> 审核通过 / 审核失败 */
    void audit(String id, String status);

    /**
     * 分配库存: 给出库单的每条明细找到库存并锁定
     *
     * @param ids 出库单id, 多个以逗号分割
     * @return 给用户看的结果说明
     */
    String allocateStock(String ids);
}
