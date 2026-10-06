package org.jeecg.modules.wms.inorder.service;

import org.jeecg.modules.wms.inorder.entity.WmsStockInOrderItems;
import org.jeecg.modules.wms.inorder.entity.WmsStockInOrders;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;

/**
 * @Description: 入库单主表
 * @Author: jeecg-boot
 * @Date:   2026-10-06
 * @Version: V1.0
 */
public interface IWmsStockInOrdersService extends IService<WmsStockInOrders> {

	/**
	 * 分页查询入库单(补全货主名称、仓库名称)
	 *
	 * @param page         分页参数
	 * @param queryWrapper 查询条件
	 * @return 入库单分页数据
	 */
	IPage<WmsStockInOrders> pageList(Page<WmsStockInOrders> page, Wrapper<WmsStockInOrders> queryWrapper);

	/**
	 * 添加入库单: 只添加入库单主表, 自动生成入库单号, 状态为初始
	 *
	 * @param wmsStockInOrders 入库单
	 */
	void add(WmsStockInOrders wmsStockInOrders);

	/**
	 * 添加一对多(excel导入时使用)
	 *
	 * @param wmsStockInOrders
	 * @param wmsStockInOrderItemsList
	 */
	public void saveMain(WmsStockInOrders wmsStockInOrders,List<WmsStockInOrderItems> wmsStockInOrderItemsList) ;

	/**
	 * 修改一对多: 修改入库单, 入库明细采用先删除再添加的方式
	 *
	 * @param wmsStockInOrders
	 * @param wmsStockInOrderItemsList
	 */
	public void updateMain(WmsStockInOrders wmsStockInOrders,List<WmsStockInOrderItems> wmsStockInOrderItemsList);

	/**
	 * 删除入库单: 初始状态直接删除, 审核失败状态更新为已作废, 其它状态不允许删除
	 *
	 * @param id
	 */
	public void delMain (String id);

	/**
	 * 批量删除入库单
	 *
	 * @param idList
	 */
	public void delBatchMain (Collection<? extends Serializable> idList);

	/**
	 * 提交审核: 初始状态、审核失败状态 -> 提交审核
	 *
	 * @param id 入库单id
	 */
	void submitAudit(String id);

	/**
	 * 审核入库单: 提交审核状态 -> 审核通过 / 审核失败
	 *
	 * @param id     入库单id
	 * @param status 审核结果: APPROVED 审核通过, REJECTED 审核失败
	 */
	void audit(String id, String status);

	/**
	 * 根据id查询入库单并加行锁(select ... for update), 必须在事务中调用
	 *
	 * @param id 入库单id
	 * @return 入库单, 不存在时返回 null
	 */
	WmsStockInOrders getByIdForUpdate(String id);

	/**
	 * 收货后更新入库单的实际收货总量、不良品总数量和状态
	 * 所有明细都收货完成时入库单状态为"收货完成", 否则为"收货中"
	 *
	 * @param stockInOrderId 入库单id
	 * @return true: 入库单已收货完成
	 */
	boolean updateReceivedStatus(String stockInOrderId);
}
