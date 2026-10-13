package org.jeecg.modules.wms.outorder.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.PageDTO;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.exception.JeecgBootBizTipException;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.util.DateUtils;
import org.jeecg.common.util.RedisUtil;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.modules.wms.config.WarehouseDictEnum;
import org.jeecg.modules.wms.inventory.entity.WmsInventory;
import org.jeecg.modules.wms.inventory.service.IWmsInventoryService;
import org.jeecg.modules.wms.inventory.service.impl.WmsInventoryTransByAllocation;
import org.jeecg.modules.wms.inventory.vo.WmsInventoryTransParam;
import org.jeecg.modules.wms.outorder.entity.WmsOutOrders;
import org.jeecg.modules.wms.outorder.entity.WmsOutOrdersAllocation;
import org.jeecg.modules.wms.outorder.entity.WmsOutOrdersItems;
import org.jeecg.modules.wms.outorder.mapper.WmsOutOrdersItemsMapper;
import org.jeecg.modules.wms.outorder.mapper.WmsOutOrdersMapper;
import org.jeecg.modules.wms.outorder.service.IWmsOutOrdersAllocationService;
import org.jeecg.modules.wms.outorder.service.IWmsOutOrdersItemsService;
import org.jeecg.modules.wms.outorder.service.IWmsOutOrdersService;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @Description: 出库单
 * @Author: jeecg-boot
 * @Date:   2025-05-19
 * @Version: V1.0
 */
@Service
public class WmsOutOrdersServiceImpl extends ServiceImpl<WmsOutOrdersMapper, WmsOutOrders> implements IWmsOutOrdersService {

	@Autowired
	private WmsOutOrdersMapper wmsOutOrdersMapper;
	@Autowired
	private WmsOutOrdersItemsMapper wmsOutOrdersItemsMapper;
	@Autowired
	private IWmsOutOrdersItemsService wmsOutOrdersItemsService;
	//注入库存service
	@Autowired
	private IWmsInventoryService iWmsInventoryService;

	//分配库存时的库存变更(锁定库存)
	@Autowired
	private WmsInventoryTransByAllocation wmsInventoryTransByAllocation;

	//注入自己
	@Autowired
	private IWmsOutOrdersService owner;

	//注入wmsOutOrdersAllocationService
	@Autowired
	private IWmsOutOrdersAllocationService outOrdersAllocationService;

	@Autowired
	private RedisUtil redisUtil;

	@Override
	public IPage<WmsOutOrders> queryList(WmsOutOrders wmsOutOrders, Integer pageNo, Integer pageSize) {
		Page<WmsOutOrders> page = PageHelper.startPage(pageNo, pageSize);
		List<WmsOutOrders> list = wmsOutOrdersMapper.queryList(wmsOutOrders);
		PageDTO<WmsOutOrders> wmsOutOrdersPageDTO = new PageDTO<>();
		wmsOutOrdersPageDTO.setRecords(list);
		wmsOutOrdersPageDTO.setTotal(page.getTotal());
		wmsOutOrdersPageDTO.setSize(page.getPageSize());
		wmsOutOrdersPageDTO.setCurrent(page.getPageNum());
		wmsOutOrdersPageDTO.setPages(page.getPages());
		return wmsOutOrdersPageDTO;
	}
	/**
	 * 新增出库单
	 */
	public void add(WmsOutOrders wmsOutOrders){
		//生成出库单号
		String orderNumber = generateOrderNumber();
		wmsOutOrders.setOrderNo(orderNumber);
		//状态默认为创建状态 "CREATED"
		wmsOutOrders.setStatus(WarehouseDictEnum.OUTBOUND_CREATED.getCode());
		wmsOutOrdersMapper.insert(wmsOutOrders);
	}
	@Override
	@Transactional(rollbackFor = Exception.class)
	public void saveMain(WmsOutOrders wmsOutOrders, List<WmsOutOrdersItems> wmsOutOrdersItemsList) {
		//生成出库单号
		String orderNumber = generateOrderNumber();
		wmsOutOrders.setOrderNo(orderNumber);
		//状态默认为创建状态 "CREATED"
		wmsOutOrders.setStatus(WarehouseDictEnum.OUTBOUND_CREATED.getCode());
		wmsOutOrdersMapper.insert(wmsOutOrders);
		//总商品数量
		int totalQuantity = 0;
		if(wmsOutOrdersItemsList!=null && wmsOutOrdersItemsList.size()>0) {
			for(WmsOutOrdersItems entity:wmsOutOrdersItemsList) {
				//商品数量
				totalQuantity += entity.getExpectedQuantity();
				//外键设置
				entity.setOrderId(wmsOutOrders.getId());
				//状态默认为创建状态 "CREATED"
				entity.setStatus(WarehouseDictEnum.OUTBOUND_DETAIL_CREATED.getCode());
				wmsOutOrdersItemsMapper.insert(entity);
			}
		}
		//更新商品数量
		wmsOutOrders.setTotalQuantity(totalQuantity);
		//商品sku种类数
		wmsOutOrders.setTotalSku(wmsOutOrdersItemsList.size());
		wmsOutOrdersMapper.updateById(wmsOutOrders);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void updateMain(WmsOutOrders wmsOutOrders,List<WmsOutOrdersItems> wmsOutOrdersItemsList) {
		wmsOutOrdersMapper.updateById(wmsOutOrders);

		//1.先删除子表数据
		wmsOutOrdersItemsMapper.deleteByMainId(wmsOutOrders.getId());

		//总商品数量
		int totalQuantity = 0;
		if(wmsOutOrdersItemsList!=null && wmsOutOrdersItemsList.size()>0) {
			for(WmsOutOrdersItems entity:wmsOutOrdersItemsList) {
				//商品数量
				totalQuantity += entity.getExpectedQuantity();
				//外键设置
				entity.setOrderId(wmsOutOrders.getId());
				//状态默认为创建状态 "CREATED"
				entity.setStatus(WarehouseDictEnum.OUTBOUND_DETAIL_CREATED.getCode());
				wmsOutOrdersItemsMapper.insert(entity);
			}
		}
		//更新商品数量
		wmsOutOrders.setTotalQuantity(totalQuantity);
		//商品sku种类数
		wmsOutOrders.setTotalSku(wmsOutOrdersItemsList.size());
		wmsOutOrdersMapper.updateById(wmsOutOrders);

	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void delMain(String id) {
		//出库单状态为创建状态或审核失败状态可以删除
		WmsOutOrders wmsOutOrders = wmsOutOrdersMapper.selectById(id);
		if(!(WarehouseDictEnum.OUTBOUND_CREATED.getCode().equals(wmsOutOrders.getStatus())
				|| WarehouseDictEnum.OUTBOUND_REJECTED.getCode().equals(wmsOutOrders.getStatus()))){
			throw new JeecgBootException("非创建状态、审核失败状态出库单不允许删除");
		}
		wmsOutOrdersItemsMapper.deleteByMainId(id);
		wmsOutOrdersMapper.deleteById(id);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void delBatchMain(Collection<? extends Serializable> idList) {
		for(Serializable id:idList) {
			//出库单状态为创建状态或审核失败状态可以删除
			WmsOutOrders wmsOutOrders = wmsOutOrdersMapper.selectById(id);
			if(!(WarehouseDictEnum.OUTBOUND_CREATED.getCode().equals(wmsOutOrders.getStatus())
					|| WarehouseDictEnum.OUTBOUND_REJECTED.getCode().equals(wmsOutOrders.getStatus()))){
				throw new JeecgBootException("非创建状态、审核失败状态出库单不允许删除");
			}
			wmsOutOrdersItemsMapper.deleteByMainId(id.toString());
			wmsOutOrdersMapper.deleteById(id);
		}
	}

	public String generateOrderNumber() {
		//当前8位时间戳(年月日)"-"
		String time = DateUtils.now().substring(0, 10).replace("-", "");
		//key
		String key = "asn_number"+time;
		long incr = redisUtil.incr(key, 1);
		if(incr == 1){
			//设置过期时间，设置24小时+10秒的目的是避免并发产生订单号重复
			redisUtil.expire(key, 24*60*60+10);
		}
		//将incr组成4位字符串
		String incrStr = String.format("%04d", incr);
		String orderNumber = "OBD"+time+incrStr;

		return orderNumber;
	}

	/**
	 * 分配库存
	 * @param orderId 出库单ID
	 * @return 分配状态 ："ALLOCATED"-分配成功 分配失败 "ALLOCATED_FAILED"-分配失败
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public String allocateStock(String orderId) {
		//审核通过或分配失败 才需要分配库存
		//通过id查询出库单信息
		WmsOutOrders outOrders = getById(orderId);
		if(null == outOrders){
			throw new JeecgBootBizTipException("出库单信息不存在!");
		}
		//状态判断
		if(!WarehouseDictEnum.OUTBOUND_APPROVED.getCode().equals(outOrders.getStatus())
				&& !WarehouseDictEnum.OUTBOUND_FAILED.getCode().equals(outOrders.getStatus())){
			throw new JeecgBootBizTipException("审核通过或分配失败的出库单才需要分配库存");
		}
		//出库单没有指定仓库则不允许分配库存(需求 3.5.4 前置条件)
		if(StringUtils.isBlank(outOrders.getWarehouseId())){
			throw new JeecgBootBizTipException("出库单没有指定仓库, 不能分配库存");
		}
		//查询未分配的出库单明细 WHERE order_id=? and status in(created,fail)
		List<WmsOutOrdersItems> outOrdersItemsList = wmsOutOrdersItemsService.lambdaQuery()
				.eq(WmsOutOrdersItems::getOrderId, orderId)
				.in(WmsOutOrdersItems::getStatus, List.of(WarehouseDictEnum.OUTBOUND_DETAIL_CREATED.getCode(),
						WarehouseDictEnum.OUTBOUND_DETAIL_FAILED.getCode())).list();
		//非空判断
		if(CollUtil.isNotEmpty(outOrdersItemsList)){
			//遍历未分配的出库单明细
			for (WmsOutOrdersItems wmsOutOrdersItems : outOrdersItemsList) {
				//查询可用库存列表
				List<WmsInventory> inventoryList = iWmsInventoryService.selectAvailableBySku(outOrders.getWarehouseId(), wmsOutOrdersItems);
				if(CollUtil.isNotEmpty(inventoryList)){
					//分配库存
					allocateStockToItems(wmsOutOrdersItems, inventoryList);
					//更新出库单明细的分配状态
					String result = wmsOutOrdersItemsService.updateAllocateStatus(wmsOutOrdersItems.getId());
					if(WarehouseDictEnum.OUTBOUND_DETAIL_FAILED.getCode().equals(result)){
						//明细分配失败则报错: 抛出异常后事务回滚, 这张出库单已经锁定的库存全部释放
						throw new JeecgBootBizTipException("分配库存失败!");
					}
				}else{
					throw new JeecgBootBizTipException("没有可用的库存!");
				}
			}
			//更新出库单状态
			return updateAllocateStatus(orderId);
		}
		//如果没有需要分配库存的明细，则代表这个出库单已经分配完成
		return WarehouseDictEnum.OUTBOUND_ALLOCATED.getCode();
	}

	/**
	 * 为商品分配库存(课件 3.3)
	 * 1.遍历可用库存 2.添加库存分配记录 3.存储库存表(锁定库存) 4.更新出库单明细的分配数量
	 * @param wmsOutOrdersItems 需分配的出库明细
	 * @param inventoryList 可用库存明细
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public void allocateStockToItems(WmsOutOrdersItems wmsOutOrdersItems, List<WmsInventory> inventoryList) {
		//取出预期数量, 例如 100
		Integer expectedQuantity = wmsOutOrdersItems.getExpectedQuantity();
		if (expectedQuantity == null || expectedQuantity <= 0) {
			return;
		}
		//遍历可用库存列表
		for (WmsInventory wmsInventory : inventoryList) {
			//取出库存可用数量, 例如 30 或 110
			Integer availableQuantity = wmsInventory.getAvailableQuantity();
			if (availableQuantity == null || availableQuantity <= 0) {
				continue;
			}
			//取小者即为此次分配数: 30 或 100
			int minValue = Math.min(expectedQuantity, availableQuantity);
			//分配后还差多少: 100-30=70 或 100-100=0
			int left = expectedQuantity - minValue;

			//批次号: 以实际被分配的这条库存的批次号为准
			//(明细指定了批次号时两者相同; 明细没指定批次号时, 明细的批次号是空的, 必须用库存的, 否则后面锁定库存、拣货时找不到这条库存)
			String batchNumber = wmsInventory.getBatchNumber();

			//添加库存分配记录: 向库存分配表插入记录
			WmsOutOrdersAllocation wmsOutOrdersAllocation = new WmsOutOrdersAllocation();
			wmsOutOrdersAllocation.setOrderId(wmsOutOrdersItems.getOrderId());
			wmsOutOrdersAllocation.setOrderItemId(wmsOutOrdersItems.getId());
			wmsOutOrdersAllocation.setSkuId(wmsOutOrdersItems.getSkuId());
			wmsOutOrdersAllocation.setLocationCode(wmsInventory.getLocationCode());
			wmsOutOrdersAllocation.setBatchNumber(batchNumber);
			wmsOutOrdersAllocation.setContainerCode(wmsInventory.getContainerCode());
			wmsOutOrdersAllocation.setAllocatedQuantity(minValue);
			wmsOutOrdersAllocation.setInventoryId(wmsInventory.getId());
			outOrdersAllocationService.save(wmsOutOrdersAllocation);

			//存储库存(锁定库存): 可用数量减少, 分配数量增加
			WmsInventoryTransParam wmsInventoryTransParam = new WmsInventoryTransParam();
			wmsInventoryTransParam.setProductId(wmsOutOrdersItems.getSkuId());
			wmsInventoryTransParam.setExecQuantity(minValue);
			wmsInventoryTransParam.setSourceLocationCode(wmsInventory.getLocationCode());
			wmsInventoryTransParam.setWarehouseId(wmsInventory.getWarehouseId());
			wmsInventoryTransParam.setBatchNumber(batchNumber);
			wmsInventoryTransParam.setTransactionType(WarehouseDictEnum.INVENTORY_ALLOCATION.getCode());
			wmsInventoryTransParam.setOperationTime(new Date());
			wmsInventoryTransByAllocation.transfer(wmsInventoryTransParam);

			//更新出库单明细已分配数量
			wmsOutOrdersItemsService.allocateStock(wmsOutOrdersItems.getId(), minValue);
			if(left == 0){
				//分配完了,结束循环
				break;
			}
			//下个循环还需要的数量
			expectedQuantity = left;
		}
	}


	/**
	 * 更新出库单分配状态
	 */
	@Override
	public String updateAllocateStatus(String orderId) {
		//查询出库单明细
		List<WmsOutOrdersItems> skuItems = wmsOutOrdersItemsService.selectByMainId(orderId);
		//skuItem中的状态有一个是分配失败则出库单的状态为分配失败，否则分配成功，用stream流实现
		WarehouseDictEnum allocateStatus = skuItems.stream()
				.filter(item -> WarehouseDictEnum.OUTBOUND_DETAIL_FAILED.getCode().equals(item.getStatus()))
				.findAny()
				.map(item -> WarehouseDictEnum.OUTBOUND_FAILED)
				.orElse(WarehouseDictEnum.OUTBOUND_ALLOCATED);

		wmsOutOrdersMapper.update(null,
				new LambdaUpdateWrapper<WmsOutOrders>().eq(WmsOutOrders::getId,orderId)
						.and(wq->wq.eq(WmsOutOrders::getStatus, WarehouseDictEnum.OUTBOUND_APPROVED.getCode())
								.or()
								.eq(WmsOutOrders::getStatus, WarehouseDictEnum.OUTBOUND_FAILED.getCode()))
						.set(WmsOutOrders::getStatus,allocateStatus.getCode()));
		//返回分配状态
		return allocateStatus.getCode();
	}

	/**
	 * 更新出库单的拣货状态
	 */
	public void updatePickStatus(List<String> orderIds){
		//根据出库单id查询状态非拣货完成的记录
		List<WmsOutOrdersItems> wmsOutOrdersItemsNotPicked = wmsOutOrdersItemsService.selectNotPickedOrderItemsByOrderId(orderIds);
		//提取出未分拣的出库单ID
		List<String> orderIdsNotPicked = wmsOutOrdersItemsNotPicked.stream().map(item -> item.getOrderId()).distinct().collect(Collectors.toList());
		//取出orderIds中不包含orderIdsNotPicked的记录，即取出分拣完成的出库单
		List<String> orderIdsPicked = orderIds.stream()
				.filter(orderId -> !orderIdsNotPicked.contains(orderId))
				.collect(Collectors.toList());
		if(orderIdsPicked.size()<=0){
			return;
		}
		//更新 出库单 的拣货状态为拣货完成，由拣货中更新中拣货完成
		LambdaUpdateWrapper<WmsOutOrders> set = new LambdaUpdateWrapper<WmsOutOrders>()
				.in(WmsOutOrders::getId, orderIdsPicked)
				.eq(WmsOutOrders::getStatus, WarehouseDictEnum.OUTBOUND_PICKING.getCode())
				.set(WmsOutOrders::getStatus, WarehouseDictEnum.OUTBOUND_PICKED.getCode());
		int update = wmsOutOrdersMapper.update(null, set);
		if(update<=0){
			throw new JeecgBootException("为出库单拣货数量及状态失败!");
		}
	}

	/**
	 * 提交审核
	 * @param id
	 */
	public void submitAudit(String id){
		//如果没有选择出库单则返回错误
		if( oConvertUtils.isEmpty(id)){
			//抛出异常
			throw new JeecgBootException("请选择要审核的出库单");
		}
		WmsOutOrders wmsOutOrdersEntity = getById(id);
		if(wmsOutOrdersEntity==null) {
			//异常
			throw new JeecgBootException("出库单不存在");
		}
		//当前状态只能是创建状态、审核失败状态才允许提交审核
		if(!(WarehouseDictEnum.OUTBOUND_CREATED.getCode().equals(wmsOutOrdersEntity.getStatus())
				|| WarehouseDictEnum.OUTBOUND_REJECTED.getCode().equals(wmsOutOrdersEntity.getStatus()))){
			throw new JeecgBootException("非初始状态、审核失败状态出库单不允许审核");
		}
		//更新状态为提交审核
		wmsOutOrdersEntity.setStatus(WarehouseDictEnum.OUTBOUND_SUBMIT_AUDIT.getCode());
		updateById(wmsOutOrdersEntity);
	}

	@Override
	public void audit(WmsOutOrders wmsOutOrders) {
		//如果没有选择出库单则返回错误
		if(oConvertUtils.isEmpty(wmsOutOrders.getId())){
			//抛出异常
			throw new JeecgBootException("请选择要审核的出库单");
		}
		WmsOutOrders wmsOutOrdersEntity = getById(wmsOutOrders.getId());
		if(wmsOutOrdersEntity==null) {
			//异常
			throw new JeecgBootException("出库单不存在");
		}
		//当前状态只能是提交审核状态
		if(!WarehouseDictEnum.OUTBOUND_SUBMIT_AUDIT.getCode().equals(wmsOutOrdersEntity.getStatus())){
			//异常
			throw new JeecgBootException("当前状态是提交审核状态方可进行审核");
		}
		// todo 校验出库单的完整性

		//更新状态为审核通过或审核不通过
		wmsOutOrdersEntity.setStatus(wmsOutOrders.getStatus());
		updateById(wmsOutOrdersEntity);
	}


	@Override
	public List<WmsOutOrders> selectByMainId(String mainId) {
		return wmsOutOrdersMapper.selectByMainId(mainId);
	}

	@Override
	public List<WmsOutOrders> selectByWaveId(String waveId) {
		//根据波次id查询其下的出库单id
		List<WmsOutOrders> wmsOutOrders = wmsOutOrdersMapper.selectList(new LambdaQueryWrapper<WmsOutOrders>().eq(WmsOutOrders::getWaveId, waveId));
		return wmsOutOrders;
	}

	/**
	 * 获取未分配波次的出库单,状态为已分配 ALLOCATED
	 */
	public List<WmsOutOrders> selectUnassignedOrders(String warehouseId){
		return wmsOutOrdersMapper.selectUnassignedOrders(warehouseId);
	}
	/**
	 * 根据出库单id更新订单的波次ID
	 */
	public void batchUpdateWaveId( List<String> orderIds, String waveId,String shipmentStrategy){
		wmsOutOrdersMapper.batchUpdateWaveId(orderIds, waveId,shipmentStrategy);
	}

}
