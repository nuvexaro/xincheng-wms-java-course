package org.jeecg.modules.wms.inorder.service.impl;

import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.wms.config.WarehouseDictEnum;
import org.jeecg.modules.wms.inorder.entity.WmsStockInOrders;
import org.jeecg.modules.wms.inorder.entity.WmsStockInOrderItems;
import org.jeecg.modules.wms.inorder.mapper.WmsStockInOrderItemsMapper;
import org.jeecg.modules.wms.inorder.mapper.WmsStockInOrdersMapper;
import org.jeecg.modules.wms.inorder.service.IWmsStockInOrdersService;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @Description: 入库单主表
 * @Author: jeecg-boot
 * @Date:   2026-10-06
 * @Version: V1.0
 */
@Service
public class WmsStockInOrdersServiceImpl extends ServiceImpl<WmsStockInOrdersMapper, WmsStockInOrders> implements IWmsStockInOrdersService {

	@Autowired
	private WmsStockInOrdersMapper wmsStockInOrdersMapper;
	@Autowired
	private WmsStockInOrderItemsMapper wmsStockInOrderItemsMapper;
	
	@Override
	@Transactional(rollbackFor = Exception.class)
	public void saveMain(WmsStockInOrders wmsStockInOrders, List<WmsStockInOrderItems> wmsStockInOrderItemsList) {
		wmsStockInOrdersMapper.insert(wmsStockInOrders);
		if(wmsStockInOrderItemsList!=null && wmsStockInOrderItemsList.size()>0) {
			for(WmsStockInOrderItems entity:wmsStockInOrderItemsList) {
				//外键设置
				entity.setOrderId(wmsStockInOrders.getId());
				wmsStockInOrderItemsMapper.insert(entity);
			}
		}
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void updateMain(WmsStockInOrders wmsStockInOrders,List<WmsStockInOrderItems> wmsStockInOrderItemsList) {

		//1.条件判断处理
		//入库单（初始状态、审核失败状态可以修改）
		String status = wmsStockInOrders.getStatus();
		if(!WarehouseDictEnum.INBOUND_INITIAL.getCode().equals(status)
				&& !WarehouseDictEnum.INBOUND_REJECTED.getCode().equals(status)) {
			throw new JeecgBootException("只有初始状态或审核失败状态的入库单可以修改");
		}
		//入库单明细至少添加一条
		if(wmsStockInOrderItemsList==null || wmsStockInOrderItemsList.isEmpty()) {
			throw new JeecgBootException("入库单明细至少添加一条");
		}

		//2.入库明细的操作
		//2.1 先删: 删除原有入库单明细
		wmsStockInOrderItemsMapper.deleteByMainId(wmsStockInOrders.getId());

		//2.2 添加: 同一入库单明细中商品唯一，同一商品数量累加合并，只插入一条
		Map<String, WmsStockInOrderItems> itemMap = new HashMap<>();
		int totalExpectedQuantity = 0;
		for(WmsStockInOrderItems item : wmsStockInOrderItemsList) {
			WmsStockInOrderItems existItem = itemMap.get(item.getProductId());
			if(existItem==null) {
				//map中不存在，存入map
				itemMap.put(item.getProductId(), item);
			}else {
				//map中存在，累加数量
				existItem.setExpectedQuantity(existItem.getExpectedQuantity() + item.getExpectedQuantity());
			}
			//累加每个商品的采购数量
			totalExpectedQuantity += item.getExpectedQuantity();
		}

		//2.3 批量保存到明细表中
		for(WmsStockInOrderItems item : itemMap.values()) {
			item.setOrderId(wmsStockInOrders.getId());
			wmsStockInOrderItemsMapper.insert(item);
		}

		//3.更新入库单信息: 只更新允许修改的字段(避免前端传入字段造成越权更新)
		WmsStockInOrders updateEntity = new WmsStockInOrders();
		updateEntity.setId(wmsStockInOrders.getId());
		updateEntity.setOrderType(wmsStockInOrders.getOrderType());
		updateEntity.setSourceNumber(wmsStockInOrders.getSourceNumber());
		updateEntity.setWarehouseId(wmsStockInOrders.getWarehouseId());
		updateEntity.setExpectedArrivalTime(wmsStockInOrders.getExpectedArrivalTime());
		updateEntity.setRemarks(wmsStockInOrders.getRemarks());
		updateEntity.setTotalExpectedQuantity(totalExpectedQuantity);
		wmsStockInOrdersMapper.updateById(updateEntity);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void delMain(String id) {
		wmsStockInOrderItemsMapper.deleteByMainId(id);
		wmsStockInOrdersMapper.deleteById(id);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void delBatchMain(Collection<? extends Serializable> idList) {
		for(Serializable id:idList) {
			wmsStockInOrderItemsMapper.deleteByMainId(id.toString());
			wmsStockInOrdersMapper.deleteById(id);
		}
	}
	
}
