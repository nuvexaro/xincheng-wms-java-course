package org.jeecg.modules.wms.inorder.service.impl;

import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.modules.wms.config.WarehouseDictEnum;
import org.jeecg.modules.wms.inorder.entity.WmsStockInOrderItems;
import org.jeecg.modules.wms.inorder.mapper.WmsStockInOrderItemsMapper;
import org.jeecg.modules.wms.inorder.service.IWmsStockInOrderItemsService;
import org.jeecg.modules.wms.wmstask.entity.WmsTasksRecords;
import org.jeecg.modules.wms.wmstask.mapper.WmsTasksRecordsMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @Description: 入库单明细
 * @Author: jeecg-boot
 * @Date:   2026-10-06
 * @Version: V1.0
 */
@Service
public class WmsStockInOrderItemsServiceImpl extends ServiceImpl<WmsStockInOrderItemsMapper, WmsStockInOrderItems> implements IWmsStockInOrderItemsService {

	@Autowired
	private WmsStockInOrderItemsMapper wmsStockInOrderItemsMapper;
	@Autowired
	private WmsTasksRecordsMapper wmsTasksRecordsMapper;

	@Override
	public List<WmsStockInOrderItems> selectByMainId(String mainId) {
		return wmsStockInOrderItemsMapper.selectByMainId(mainId);
	}

	/**
	 * 收货后更新入库单明细的收货数量、不良品数量和状态
	 *
	 * @param stockInOrderItemId 入库单明细id
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public void updateReceivedStatus(String stockInOrderItemId) {
		//1.通过id查询入库明细是否存在
		if (oConvertUtils.isEmpty(stockInOrderItemId)) {
			throw new JeecgBootException("入库明细id不能为空");
		}
		WmsStockInOrderItems item = getById(stockInOrderItemId);
		if (item == null) {
			throw new JeecgBootException("入库明细不存在");
		}

		//2.查询这条明细的所有"收货"记录(上架记录也带着入库明细id, 所以必须限定任务类型为收货任务)
		List<WmsTasksRecords> recordsList = wmsTasksRecordsMapper.selectList(new LambdaQueryWrapper<WmsTasksRecords>()
				.eq(WmsTasksRecords::getStockInOrderItemId, stockInOrderItemId)
				.eq(WmsTasksRecords::getTaskType, WarehouseDictEnum.TASK_TYPE_RECEIVING.getCode()));

		//3.分别计算良品数量、不良品数量
		int goodCount = 0;
		int badCount = 0;
		if (recordsList != null) {
			for (WmsTasksRecords record : recordsList) {
				int execQuantity = record.getExecQuantity() == null ? 0 : record.getExecQuantity();
				if (WarehouseDictEnum.RECEIVING_GOOD.getCode().equals(record.getInventoryAttribute())) {
					goodCount += execQuantity;
				} else if (WarehouseDictEnum.RECEIVING_DEFECTIVE.getCode().equals(record.getInventoryAttribute())) {
					badCount += execQuantity;
				}
			}
		}

		//4.良品数 + 不良品数 不能大于采购数量
		int expectedQuantity = item.getExpectedQuantity() == null ? 0 : item.getExpectedQuantity();
		if (goodCount + badCount > expectedQuantity) {
			throw new JeecgBootException("收货数量不能大于采购数量");
		}

		//5.更新: 只设置需要更新的字段
		WmsStockInOrderItems updateItem = new WmsStockInOrderItems();
		updateItem.setId(stockInOrderItemId);
		// 实际收货数量(良品)
		updateItem.setReceivedQuantity(goodCount);
		// 不良品数量
		updateItem.setDefectiveQuantity(badCount);
		// 采购数量 = 良品数 + 不良品数 时收货完成, 否则收货中
		if (goodCount + badCount == expectedQuantity) {
			updateItem.setStatus(WarehouseDictEnum.INBOUND_DETAIL_RECEIVED.getCode());
		} else {
			updateItem.setStatus(WarehouseDictEnum.INBOUND_DETAIL_RECEIVING.getCode());
		}
		boolean updated = updateById(updateItem);
		if (!updated) {
			throw new JeecgBootException("更新入库明细的收货数量和状态失败");
		}
	}

	/**
	 * 上架后更新入库单明细的上架数量和状态
	 *
	 * @param stockInOrderItemId 入库单明细id
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public void updateShelvedStatus(String stockInOrderItemId) {
		//1.通过id查询入库明细是否存在
		if (oConvertUtils.isEmpty(stockInOrderItemId)) {
			throw new JeecgBootException("入库明细id不能为空");
		}
		WmsStockInOrderItems item = getById(stockInOrderItemId);
		if (item == null) {
			throw new JeecgBootException("入库明细不存在");
		}

		//2.查询这条明细的所有"上架"记录, 累加得到上架数量
		List<WmsTasksRecords> recordsList = wmsTasksRecordsMapper.selectList(new LambdaQueryWrapper<WmsTasksRecords>()
				.eq(WmsTasksRecords::getStockInOrderItemId, stockInOrderItemId)
				.eq(WmsTasksRecords::getTaskType, WarehouseDictEnum.TASK_TYPE_PUTAWAY.getCode()));
		int shelvedCount = 0;
		if (recordsList != null) {
			for (WmsTasksRecords record : recordsList) {
				shelvedCount += record.getExecQuantity() == null ? 0 : record.getExecQuantity();
			}
		}

		//3.上架数量不能大于收货数量(良品才上架)
		int receivedQuantity = item.getReceivedQuantity() == null ? 0 : item.getReceivedQuantity();
		if (shelvedCount > receivedQuantity) {
			throw new JeecgBootException("上架数量不能大于收货数量");
		}

		//4.更新: 只设置需要更新的字段
		WmsStockInOrderItems updateItem = new WmsStockInOrderItems();
		updateItem.setId(stockInOrderItemId);
		updateItem.setShelvedQuantity(shelvedCount);
		// 上架数量 = 收货数量时上架完成
		if (shelvedCount == receivedQuantity) {
			updateItem.setStatus(WarehouseDictEnum.INBOUND_DETAIL_PUTAWAYED.getCode());
		}
		boolean updated = updateById(updateItem);
		if (!updated) {
			throw new JeecgBootException("更新入库明细的上架数量和状态失败");
		}
	}
}
