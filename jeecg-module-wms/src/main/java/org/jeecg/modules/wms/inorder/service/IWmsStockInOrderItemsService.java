package org.jeecg.modules.wms.inorder.service;

import org.jeecg.modules.wms.inorder.entity.WmsStockInOrderItems;
import com.baomidou.mybatisplus.extension.service.IService;
import java.util.List;

/**
 * @Description: 入库单明细
 * @Author: jeecg-boot
 * @Date:   2026-10-06
 * @Version: V1.0
 */
public interface IWmsStockInOrderItemsService extends IService<WmsStockInOrderItems> {

	/**
	 * 通过主表id查询子表数据(带商品编码、商品名称)
	 *
	 * @param mainId 主表id
	 * @return List<WmsStockInOrderItems>
	 */
	public List<WmsStockInOrderItems> selectByMainId(String mainId);

	/**
	 * 收货后更新入库单明细的收货数量(良品)、不良品数量和状态
	 * 良品数量 + 不良品数量 = 采购数量 时状态为"收货完成", 否则为"收货中"
	 *
	 * @param stockInOrderItemId 入库单明细id
	 */
	void updateReceivedStatus(String stockInOrderItemId);
}
