package org.jeecg.modules.wms.waybill.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.plugins.IgnoreStrategy;
import com.baomidou.mybatisplus.core.plugins.InterceptorIgnoreHelper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.PageDTO;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.aspect.annotation.Lock;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.util.DateUtils;
import org.jeecg.common.util.RedisUtil;
import org.jeecg.modules.wms.config.WarehouseDictEnum;
import org.jeecg.modules.wms.goods.entity.WmsProducts;
import org.jeecg.modules.wms.goods.service.IWmsProductsService;
import org.jeecg.modules.wms.inventory.service.impl.WmsInventoryTransByDeliver;
import org.jeecg.modules.wms.inventory.vo.WmsInventoryTransParam;
import org.jeecg.modules.wms.outorder.entity.WmsOutOrders;
import org.jeecg.modules.wms.outorder.entity.WmsOutOrdersItems;
import org.jeecg.modules.wms.outorder.service.IWmsOutOrdersItemsService;
import org.jeecg.modules.wms.outorder.service.IWmsOutOrdersService;
import org.jeecg.modules.wms.shipment.entity.WmsShipment;
import org.jeecg.modules.wms.shipment.entity.WmsShipmentDetail;
import org.jeecg.modules.wms.shipment.mapper.WmsShipmentDetailMapper;
import org.jeecg.modules.wms.shipment.mapper.WmsShipmentMapper;
import org.jeecg.modules.wms.shipment.service.IWmsShipmentDetailService;
import org.jeecg.modules.wms.shipment.service.IWmsShipmentService;
import org.jeecg.modules.wms.shipment.strategy.ShipmentGenerationStrategy;
import org.jeecg.modules.wms.shipment.strategy.ShipmentStrategyFactory;
import org.jeecg.modules.wms.shipment.vo.ShipmentGenerationResult;
import org.jeecg.modules.wms.wave.entity.WmsWaveMaster;
import org.jeecg.modules.wms.wave.service.IWmsWaveMasterService;
import org.jeecg.modules.wms.waybill.service.IWmsSfService;
import org.jeecg.modules.wms.waybill.service.IWmsWaybillService;
import org.jeecg.modules.wms.wmstask.entity.WmsTasks;
import org.jeecg.modules.wms.wmstask.entity.WmsTasksRecords;
import org.jeecg.modules.wms.wmstask.service.IWmsTasksRecordsService;
import org.jeecg.modules.wms.wmstask.service.IWmsTasksService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @Description: 运单服务接口
 * @Author: jeecg-boot
 * @Date:   2025-06-13
 * @Version: V1.0
 */
@Service
@Slf4j
public class WmsWaybillServiceImpl  implements IWmsWaybillService {

	@Autowired
	private IWmsShipmentService wmsShipmentService;

	@Autowired
	private IWmsOutOrdersItemsService wmsOutOrdersItemsService;

	@Autowired
	private IWmsTasksRecordsService wmsTasksRecordsService;

	@Autowired
	private IWmsTasksService wmsTasksService;

	@Autowired
	private WmsWaybillServiceImpl owner;

	@Autowired
	private IWmsOutOrdersService wmsOutOrdersService;
	@Autowired
	private IWmsWaveMasterService wmsWaveMasterService;

	@Autowired
	private WmsInventoryTransByDeliver wmsInventoryTransByDeliver;


	@Autowired
	private IWmsSfService sfService;


	/**
	 * 发货
	 * @param waveId
	 */
	@Transactional(rollbackFor = Exception.class)
	public void send(String waveId){
		//根据波次id查询出库单
		List<WmsOutOrders> wmsOutOrders = wmsOutOrdersService.selectByMainId(waveId);
		if (wmsOutOrders == null || wmsOutOrders.isEmpty()) {
			throw new JeecgBootException("波次下没有出库单");
		}
		//判断wmsOutOrders中是否存在未创建运单的出库单
		if(wmsOutOrders.stream().anyMatch(order -> !WarehouseDictEnum.CREATED_WAYBILL.getCode().equals(order.getCreatedWaybill()))){
			throw new JeecgBootException("波次下存在未创建运单的出库单暂时无法发货！");
		}
		//更新订单的状态为已发货
		LambdaUpdateWrapper<WmsOutOrders> eq = new LambdaUpdateWrapper<WmsOutOrders>()
				.in(WmsOutOrders::getWaveId, waveId)
				.set(WmsOutOrders::getStatus, WarehouseDictEnum.OUTBOUND_SHIPPED.getCode())
				.eq(WmsOutOrders::getStatus, WarehouseDictEnum.OUTBOUND_PACKED.getCode());
		boolean update = wmsOutOrdersService.update(null, eq);
		if(!update){
			throw new JeecgBootException("更新订单状态为已发货失败");
		}
		//更新波次状态为已发货
		LambdaUpdateWrapper<WmsWaveMaster> eq1 = new LambdaUpdateWrapper<WmsWaveMaster>()
				.eq(WmsWaveMaster::getId, waveId)
				.set(WmsWaveMaster::getStatus, WarehouseDictEnum.WAVE_SHIPPED.getCode())
				.eq(WmsWaveMaster::getStatus, WarehouseDictEnum.WAVE_PACKED.getCode());
		boolean update1 = wmsWaveMasterService.update(null, eq1);
		if(!update1){
			throw new JeecgBootException("更新波次状态为已发货失败");
		}
		//更新包裹状态为已发货
		//从wmsOutOrders中找到未打包的出库单并提取订单id
		List<String> orderIds = wmsOutOrders.stream().map(WmsOutOrders::getId).collect(Collectors.toList());
		LambdaUpdateWrapper<WmsShipment> eq2 = new LambdaUpdateWrapper<WmsShipment>()
				.in(WmsShipment::getOrderId, orderIds)
				.set(WmsShipment::getStatus, WarehouseDictEnum.PACKAGE_STATUS_SHIPPED.getCode())
				.eq(WmsShipment::getStatus, WarehouseDictEnum.PACKAGE_STATUS_PACKED.getCode());
		boolean update2 = wmsShipmentService.update(null, eq2);
		if(!update2){
			throw new JeecgBootException("更新包裹状态失败");
		}

		//更新出库单明细状态为已发货
		LambdaUpdateWrapper<WmsOutOrdersItems> eq3 = new LambdaUpdateWrapper<WmsOutOrdersItems>()
				.in(WmsOutOrdersItems::getOrderId, orderIds)
				.set(WmsOutOrdersItems::getStatus, WarehouseDictEnum.OUTBOUND_DETAIL_SHIPPED.getCode())
				.eq(WmsOutOrdersItems::getStatus, WarehouseDictEnum.OUTBOUND_DETAIL_PACKED.getCode());
				boolean update3 = wmsOutOrdersItemsService.update(null, eq3);
				if(!update3){
					throw new JeecgBootException("更新出库单明细状态为已发货失败");
				}

		//============================扣减库存==========================
		//发货位: 拣货时商品从存储位拣到了发货位(拣货记录的目的储位), 发货要扣减发货位上的库存
		//原来的代码只取了第一条拣货记录的储位, 波次里有多个商品、拣到不同储位时会扣错; 这里按 商品 + 发货位 汇总
		//  通过"这个波次的拣货任务"找拣货记录: 不依赖拣货记录上的波次id, 改代码之前拣的货也能找到
		List<String> pickTaskIds = wmsTasksService.list(new LambdaQueryWrapper<WmsTasks>()
						.eq(WmsTasks::getWaveOrderId, waveId)
						.eq(WmsTasks::getTaskType, WarehouseDictEnum.TASK_TYPE_PICKING.getCode()))
				.stream().map(WmsTasks::getId).collect(Collectors.toList());
		if (pickTaskIds.isEmpty()) {
			throw new JeecgBootException("波次下没有拣货任务，无法扣减库存");
		}
		List<WmsTasksRecords> pickRecords = wmsTasksRecordsService.list(new LambdaQueryWrapper<WmsTasksRecords>()
				.in(WmsTasksRecords::getTaskId, pickTaskIds));
		if (pickRecords.isEmpty()) {
			throw new JeecgBootException("波次下没有拣货记录，无法扣减库存");
		}
		//key=商品id, value=(发货位 -> 这个波次拣到该发货位的数量), 按拣货先后保持顺序
		Map<String, LinkedHashMap<String, Integer>> pickedMap = new LinkedHashMap<>();
		for (WmsTasksRecords record : pickRecords) {
			int qty = record.getExecQuantity() == null ? 0 : record.getExecQuantity();
			if (qty <= 0 || StringUtils.isBlank(record.getTargetLocationCode())) {
				continue;
			}
			pickedMap.computeIfAbsent(record.getProductId(), k -> new LinkedHashMap<>())
					.merge(record.getTargetLocationCode(), qty, Integer::sum);
		}
		String warehouseId = pickRecords.get(0).getTargetWarehouseId();
		//遍历出库单扣减库存
		for (WmsOutOrders outOrder : wmsOutOrders){
			//查询出库单明细
			List<WmsOutOrdersItems> wmsOutOrdersItems = wmsOutOrdersItemsService.selectByMainId(outOrder.getId());
			for (WmsOutOrdersItems item : wmsOutOrdersItems){
				//变更数量是打包的数量(课件 9.8)
				int need = item.getPackedQuantity() == null ? 0 : item.getPackedQuantity();
				if (need <= 0) {
					continue;
				}
				Map<String, Integer> locations = pickedMap.get(item.getSkuId());
				if (locations == null || locations.isEmpty()) {
					throw new JeecgBootException("出库单" + outOrder.getOrderNo() + "的商品没有拣货记录，无法扣减库存");
				}
				//从这个商品的各个发货位依次扣, 直到扣够打包数量
				for (Map.Entry<String, Integer> location : locations.entrySet()) {
					if (need <= 0) {
						break;
					}
					int take = Math.min(need, location.getValue());
					if (take <= 0) {
						continue;
					}
					WmsInventoryTransParam inventoryTransParam = new WmsInventoryTransParam();
					inventoryTransParam.setProductId(item.getSkuId());
					inventoryTransParam.setExecQuantity(take);
					inventoryTransParam.setSourceLocationCode(location.getKey());
					inventoryTransParam.setWarehouseId(warehouseId);
					inventoryTransParam.setBatchNumber(item.getBatchNumber());
					inventoryTransParam.setTransactionType(WarehouseDictEnum.INVENTORY_OUTBOUND.getCode());
					inventoryTransParam.setRemarks(outOrder.getOrderNo());
					inventoryTransParam.setOperationTime(new Date());
					wmsInventoryTransByDeliver.transfer(inventoryTransParam);
					//这个发货位还剩多少可以扣(同一商品的下一个出库单接着扣)
					location.setValue(location.getValue() - take);
					need -= take;
				}
				if (need > 0) {
					throw new JeecgBootException("出库单" + outOrder.getOrderNo() + "的打包数量大于拣货数量，无法扣减库存");
				}
			}
		}
	}


	/**
	 * 根据波次id查询运单id
	 * @param waveId 波次id
	 * @return 运单id
	 */
	/**
	 * 根据波次id查询打印面单的数据
	 * 原来前端把整个波次的运单号当成"一个主单 + 若干子单"来打印, 波次里有多个出库单、或者主单不在第一个时,
	 * 顺丰会提示"获取数据失败"。这里按出库单分组: 每个出库单的第一个包裹是主单, 其余是这个主单的子单
	 */
	@Override
	public List<Map<String, Object>> selectPrintDocumentsByWaveId(String waveId) {
		//根据波次id查询出库单
		List<WmsOutOrders> wmsOutOrders = wmsOutOrdersService.selectByMainId(waveId);
		if (wmsOutOrders == null || wmsOutOrders.isEmpty()) {
			throw new JeecgBootException("波次下没有出库单");
		}
		if(wmsOutOrders.stream().anyMatch(order -> !WarehouseDictEnum.CREATED_WAYBILL.getCode().equals(order.getCreatedWaybill()))){
			throw new JeecgBootException("波次下存在未创建运单的出库单，请稍后再试！");
		}
		//一次查出波次下所有出库单的包裹(不再一张出库单查一次), 按出库单分组
		//每组按包裹id排序(和保存运单号时的顺序一致, 第一个是主单)
		Map<String, List<WmsShipment>> shipmentsByOrder = wmsShipmentService.list(new LambdaQueryWrapper<WmsShipment>()
						.in(WmsShipment::getOrderId, wmsOutOrders.stream().map(WmsOutOrders::getId).collect(Collectors.toList())))
				.stream()
				.filter(sh -> StringUtils.isNotBlank(sh.getTrackingNo()))
				.sorted(Comparator.comparing(WmsShipment::getId))
				.collect(Collectors.groupingBy(WmsShipment::getOrderId));
		List<Map<String, Object>> documents = new ArrayList<>();
		for (WmsOutOrders order : wmsOutOrders) {
			List<WmsShipment> shipments = shipmentsByOrder.get(order.getId());
			if (shipments == null || shipments.isEmpty()) {
				continue;
			}
			String masterWaybillNo = shipments.get(0).getTrackingNo();
			for (int i = 0; i < shipments.size(); i++) {
				Map<String, Object> document = new HashMap<>();
				document.put("masterWaybillNo", masterWaybillNo);
				//第一个是主单, 不传子单号; 其余的是子单
				if (i > 0) {
					document.put("branchWaybillNo", shipments.get(i).getTrackingNo());
				}
				document.put("seq", i + 1);
				document.put("sum", shipments.size());
				document.put("remark", "");
				documents.add(document);
			}
		}
		if (documents.isEmpty()) {
			throw new JeecgBootException("波次下的包裹还没有运单号");
		}
		return documents;
	}

	public List<String> selectWaybillNosByWaveId(String waveId){
		//根据波次id查询出库单
		List<WmsOutOrders> wmsOutOrders = wmsOutOrdersService.selectByMainId(waveId);
		if (wmsOutOrders == null || wmsOutOrders.isEmpty()) {
			throw new JeecgBootException("波次下没有出库单");
		}
		//判断wmsOutOrders中是否存在未创建运单的出库单
		if(wmsOutOrders.stream().anyMatch(order -> !WarehouseDictEnum.CREATED_WAYBILL.getCode().equals(order.getCreatedWaybill()))){
			throw new JeecgBootException("波次下存在未创建运单的出库单，请稍后再试！");
		}
		//根据订单id查询包裹并得到运单号
		LambdaQueryWrapper<WmsShipment> in = new LambdaQueryWrapper<WmsShipment>()
				.in(WmsShipment::getOrderId, wmsOutOrders.stream().map(WmsOutOrders::getId).collect(Collectors.toList()));
		List<WmsShipment> wmsShipments = wmsShipmentService.list(in);
		//获取运单号(去掉没有运单号的包裹)
		List<String> waybillNos = wmsShipments.stream().map(WmsShipment::getTrackingNo)
				.filter(StringUtils::isNotBlank).collect(Collectors.toList());
		if (waybillNos.isEmpty()) {
			throw new JeecgBootException("波次下的包裹还没有运单号");
		}
		return waybillNos;
	}

	/**
	 * 请求顺丰生成指定订单的运单
	 * 首先请求顺丰下单，成功后生成运单
	 * 分布式锁(第三章 9.7): 系统部署多个实例时, 每个实例的定时任务都会处理同一批出库单,
	 * 加锁后同一个出库单同一时刻只有一个线程能生成运单, 拿不到锁的直接放弃(下一轮再看)
	 */
	@Lock(formatter = "ORDERS:CREATEWAYBILL:LOCK:#{orderId}", startDog = true)
	public void generateWaybillForOrder(String orderId) throws Exception {
		// 查询出库单
		WmsOutOrders order = wmsOutOrdersService.getById(orderId);
		if (order == null) {
			throw new JeecgBootException("出库单" + orderId + "不存在");
		}
		// 如果状态不是打包完成，则不能生成运单
		if (!WarehouseDictEnum.OUTBOUND_PACKED.getCode().equals(order.getStatus())) {
			throw new JeecgBootException("出库单" + order.getOrderNo() + "状态不是打包完成，不能生成运单");
		}
		// 如果运单已经生成，则不能重复生成
		if (WarehouseDictEnum.CREATED_WAYBILL.getCode().equals(order.getCreatedWaybill())) {
			throw new JeecgBootException("出库单" + order.getOrderNo() + "已生成运单，不能重复生成");
		}
		// 查询该订单下的包裹
		List<WmsShipment> shipments = wmsShipmentService.selectByOrderId(orderId);
		if (shipments == null || shipments.isEmpty()) {
			throw new JeecgBootException("出库单" + order.getOrderNo() + "没有包裹，不能生成运单");
		}
		// 包裹按id排序: 保存运单号和打印面单都按这个顺序, 第一个包裹存主单号, 打印时才能对上
		shipments = new ArrayList<>(shipments);
		shipments.sort(Comparator.comparing(WmsShipment::getId));
		// 调用顺丰创建运单: 一个包裹对应一个运单号
		List<String> waybillNos = sfService.requestSfCreateOrder(order, shipments);
		if (waybillNos == null || waybillNos.size() != shipments.size()) {
			throw new JeecgBootException("出库单" + order.getOrderNo() + "请求顺丰下单没有拿到和包裹数量一致的运单号");
		}
		// 保存运单号: 通过 owner(本类的 spring 代理对象)调用, saveWaybill 上的事务才会生效
		// (请求顺丰是网络调用, 放在事务外面; 只有写数据库的部分放在事务里)
		owner.saveWaybill(waybillNos, shipments);
	}

	/**
	 * 保存运单号
	 * @param waybillNos 运单号
	 * @param shipments 包裹
	 */
	@Transactional(rollbackFor = Exception.class)
	public void saveWaybill(List<String> waybillNos,List<WmsShipment> shipments){
		//如果运单号的数量和包裹数量不一致不能继续，抛出异常
		if(waybillNos.size() != shipments.size()){
			throw new RuntimeException("运单数量和包裹数量不一致");
		}
		//将运单号依次保存到包裹中
		for (int i = 0; i < waybillNos.size(); i++) {
			LambdaUpdateWrapper<WmsShipment> set = new LambdaUpdateWrapper<WmsShipment>()
					.eq(WmsShipment::getId, shipments.get(i).getId())
					.set(WmsShipment::getTrackingNo, waybillNos.get(i));
			wmsShipmentService.update(null,set);
		}
		WmsShipment wmsShipment = shipments.get(0);
		//订单id
		String orderId = wmsShipment.getOrderId();
		//更新订单"createdWaybill"为1
		LambdaUpdateWrapper<WmsOutOrders> update = new LambdaUpdateWrapper<WmsOutOrders>()
				.eq(WmsOutOrders::getId, orderId)
				.set(WmsOutOrders::getCreatedWaybill, "1");
		wmsOutOrdersService.update(null, update);
	}



}
