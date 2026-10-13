package org.jeecg.modules.wms.wave.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.PageDTO;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import org.jeecg.common.exception.JeecgBootBizTipException;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.wms.config.WarehouseDictEnum;
import org.jeecg.modules.wms.outorder.entity.WmsOutOrders;
import org.jeecg.modules.wms.outorder.entity.WmsOutOrdersAllocation;
import org.jeecg.modules.wms.outorder.mapper.WmsOutOrdersAllocationMapper;
import org.jeecg.modules.wms.outorder.service.IWmsOutOrdersItemsService;
import org.jeecg.modules.wms.outorder.service.IWmsOutOrdersService;
import org.jeecg.modules.wms.wave.entity.WmsWaveMaster;
import org.jeecg.modules.wms.wave.entity.WmsWaveSkuSummary;
import org.jeecg.modules.wms.wave.mapper.WmsWaveMasterMapper;
import org.jeecg.modules.wms.wave.service.IWaveStrategyService;
import org.jeecg.modules.wms.wave.service.IWmsWaveMasterService;
import org.jeecg.modules.wms.wave.service.IWmsWaveSkuSummaryService;
import org.jeecg.modules.wms.wave.strategy.IWaveStrategy;
import org.jeecg.modules.wms.wave.strategy.SIFQWaveStrategy;
import org.jeecg.modules.wms.wave.strategy.WaveCreateClient;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.transaction.annotation.Transactional;
import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @Description: 波次主表
 * @Author: jeecg-boot
 * @Date:   2025-06-03
 * @Version: V1.0
 */
@Service
public class WmsWaveMasterServiceImpl extends ServiceImpl<WmsWaveMasterMapper, WmsWaveMaster> implements IWmsWaveMasterService {

	@Autowired
	private WmsWaveMasterMapper wmsWaveMasterMapper;
	@Autowired
	private IWmsOutOrdersService wmsOutOrdersService;

	@Autowired
	private WmsOutOrdersAllocationMapper wmsOutOrdersAllocationMapper;

	@Autowired
	private IWmsWaveSkuSummaryService iWmsWaveSkuSummaryService;
	@Autowired
	private IWaveStrategyService waveStrategyService;
	@Autowired
	private IWmsOutOrdersItemsService wmsOutOrdersItemsService;

	/**
	 * 注入所有的波次策略实现对象(带 @Component 的策略类)
	 * 加 @Lazy 的原因: 波次策略里又注入了本类(创建波次要调用 addWave), 两边互相依赖,
	 * 加上 @Lazy 后本类创建时不需要先把策略对象建好, 第一次用到时再去取, 避免启动时报循环依赖
	 */
	@Lazy
	@Autowired
	private List<IWaveStrategy> strategies;


	@Override
	public IPage<WmsWaveMaster> queryList(WmsWaveMaster wmsWaveMaster, Integer pageNo, Integer pageSize) {
		//开启分页查询,当执行查询时，插件进行相关的sql拦截进行分页操作，返回一个page对象
		Page<WmsWaveMaster> page = PageHelper.startPage(pageNo, pageSize);
		List<WmsWaveMaster> list = wmsWaveMasterMapper.queryList(wmsWaveMaster);
		PageDTO<WmsWaveMaster> wmsWaveMasterPageDTO = new PageDTO<>();
		wmsWaveMasterPageDTO.setRecords(list);
		wmsWaveMasterPageDTO.setTotal(page.getTotal());
		wmsWaveMasterPageDTO.setSize(page.getPageSize());
		wmsWaveMasterPageDTO.setCurrent(page.getPageNum());
		wmsWaveMasterPageDTO.setPages(page.getPages());
		return wmsWaveMasterPageDTO;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void delMain(String id) {
//		wmsOutOrdersMapper.deleteByMainId(id);
		wmsWaveMasterMapper.deleteById(id);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void delBatchMain(Collection<? extends Serializable> idList) {
		for(Serializable id:idList) {
//			wmsOutOrdersMapper.deleteByMainId(id.toString());
			wmsWaveMasterMapper.deleteById(id);
		}
	}


//
//	/**
//	 * 处理订单波次分配
//	 */
//	public void process(List<WmsOutOrders> orders,
//						Map<String, List<WmsOutOrdersAllocation>> allocationsMap,
//						List<String> selectedStrategies) {
//		// 按优先级排序策略
//		this.strategies = strategies.stream()
//				.sorted((s1, s2) -> Integer.compare(s1.getPriority(), s2.getPriority()))
//				.collect(Collectors.toList());
//		List<WmsOutOrders> remainingOrders = new ArrayList<>(orders);
//
//		// 只处理选中的策略并按优先级排序
//		List<IWaveStrategy> filteredStrategies = strategies.stream()
//				.filter(strategy -> selectedStrategies.contains(strategy.getStrategyType()))
//				.sorted((s1, s2) -> Integer.compare(s1.getPriority(), s2.getPriority()))
//				.collect(Collectors.toList());
//
//		for (IWaveStrategy strategy : filteredStrategies) {
//			remainingOrders = strategy.process(remainingOrders, allocationsMap);
//			if (remainingOrders.isEmpty()) {
//				break;
//			}
//		}
//	}

	@Override
	@Transactional
	public WmsWaveMaster addWave(List<WmsOutOrders> orders, String strategyType,String shipmentStrategy) {
		if (orders.isEmpty()) {
			return null;
		}

		// 创建波次记录
		WmsWaveMaster wave = new WmsWaveMaster();
		wave.setWaveNo(generateWaveNo(strategyType));
		wave.setWaveRuleId(strategyType);
		wave.setStatus(WarehouseDictEnum.WAVE_CREATED.getCode());
		wave.setTotalOrders(orders.size());

		// 计算波次总商品数量和SKU种类数
		int totalQuantity = orders.stream().mapToInt(WmsOutOrders::getTotalQuantity).sum();
		wave.setTotalSkus(totalQuantity);
		//计算波次总商品种类数
		int totalItems = orders.stream().mapToInt(WmsOutOrders::getTotalSku).sum();
		wave.setTotalItems(totalItems);
		wave.setCreateTime(new Date());
		//仓库
		wave.setWarehouseId(orders.get(0).getWarehouseId());
		// 保存波次
		save(wave);

		// 根据出库单id更新订单的波次ID
		List<String> orderIds = orders.stream()
				.map(WmsOutOrders::getId)
				.collect(Collectors.toList());
		wmsOutOrdersService.batchUpdateWaveId(orderIds, wave.getId(), shipmentStrategy);

		return wave;
	}

	/**
	 * 更新波次主表的拣货状态，如果所有波次下出库单的状态为已拣货，则更新波次主表的拣货状态为拣货完成
	 * @param waveId 波次ID
	 */
	@Transactional(rollbackFor = Exception.class)
	public void updatePickStatus(String waveId){
		WmsWaveMaster wave = getById(waveId);
		if (wave == null) {
			//抛出异常
			throw new JeecgBootException("波次不存在");
		}
		//根据波次id查询波次拣货任务明细
		List<WmsWaveSkuSummary> skuSummaries = iWmsWaveSkuSummaryService.selectByWaveId(waveId);

		if (skuSummaries.isEmpty()) {
			return;
		}
		boolean allPicked = skuSummaries.stream()
				.allMatch(skuSummary -> WarehouseDictEnum.WAVESKU_PICKED.getCode().equals(skuSummary.getStatus()));
		if(allPicked){
			//计算总拣货数量
			int pickedQuantitySum = skuSummaries.stream().mapToInt(WmsWaveSkuSummary::getPickedQuantity).sum();
			if(pickedQuantitySum <=0){
				throw new JeecgBootException("波次拣货数量必须大于0");
			}
			wave.setPickedQuantity(pickedQuantitySum);
			wave.setStatus(WarehouseDictEnum.WAVE_PICKED.getCode());
			boolean b = updateById(wave);
			if (!b) {
				throw new JeecgBootException("更新波次拣货数量失败!");
			}
		}

	}

	/**
	 * 向波次表增加分拣数量
	 */
	@Override
	public void addSortingQuantity(String waveId, Integer sortingQuantity) {
		//查询波次
		WmsWaveMaster wave = getById(waveId);
		if (wave == null) {
			throw new JeecgBootException("波次不存在");
		}
		//拣货数量
		Integer pickedQuantity = wave.getPickedQuantity();
		LambdaUpdateWrapper<WmsWaveMaster> updateWrapper = new LambdaUpdateWrapper<WmsWaveMaster>()
				.eq(WmsWaveMaster::getId, waveId)
				.setSql("sorting_quantity = sorting_quantity + {0}", sortingQuantity)
				.le(WmsWaveMaster::getSortingQuantity, pickedQuantity-sortingQuantity);
		boolean update = update(null, updateWrapper);
		if (!update) {
			throw new JeecgBootException("更新分拣数量失败!");
		}
	}


	@Override
	public void updateCompleteSortingOrderQuantity(List<String> waveIds) {
		for (String waveId : waveIds) {
			//根据波次id查询出库单，并且找到出库单状态为拣货完成的数量,一个出库单算一个数量
			List<WmsOutOrders> orders = wmsOutOrdersService.selectByWaveId(waveId);
			if (orders.isEmpty()) {
				return;
			}
			long count = orders.stream()
					.filter(order -> WarehouseDictEnum.OUTBOUND_PICKED.getCode().equals(order.getStatus()))
					.count();
			WmsWaveMaster wave = new WmsWaveMaster();
			wave.setId(waveId);
			wave.setSortingOrderQuantity((int) count);
			updateById(wave);
		}

	}
	@Override
	@Transactional(rollbackFor = Exception.class)
	public void createWave(String warehouseId,List<String> strategyCodes) {
		// 1. 获取已分配库存且未创建波次的出库单
		List<WmsOutOrders> unassignedOrders = wmsOutOrdersService.selectUnassignedOrders(warehouseId);
		if (unassignedOrders == null || unassignedOrders.isEmpty()) {
			// 课件这里是直接 return, 页面上却会提示"创建成功", 容易误会, 所以改成提示原因
			throw new JeecgBootBizTipException("该仓库没有需要创建波次的出库单(出库单状态要为已分配, 且还没有创建波次)");
		}
		// 责任链里会从这个列表里移除已处理的出库单, 所以换成一个可以修改的列表
		unassignedOrders = new ArrayList<>(unassignedOrders);

		// 2. 获取这些出库单的分配明细
		List<String> orderIds = unassignedOrders.stream()
				.map(WmsOutOrders::getId)
				.collect(Collectors.toList());
		Map<String, List<WmsOutOrdersAllocation>> allocationsMap = wmsOutOrdersAllocationMapper.selectAllocationsByOrderIds(orderIds)
				.stream()
				.collect(Collectors.groupingBy(WmsOutOrdersAllocation::getOrderId));

		// 3.使用责任链处理波次创建
		//   复制一份策略列表再往里加: 课件是直接往注入的 strategies 里 add,
		//   那样每创建一次波次列表里就多一个"一品2件", 越积越多, 所以这里用一个新列表
		List<IWaveStrategy> allStrategies = new ArrayList<>(strategies);
		// 补充缺少的1品N件策略对象: 1品2件(编码 SIFQ_2)
		// 如果以后要用1品3件, 在波次策略表加一条编码为 SIFQ_3 的策略, 再在这里加一行 quantity 为 3 的即可
		allStrategies.add(new SIFQWaveStrategy(this, waveStrategyService, wmsOutOrdersItemsService, 2));
		WaveCreateClient client = new WaveCreateClient(allStrategies, strategyCodes);
		// 调用责任链处理第一个处理器来创建波次
		int beforeCount = unassignedOrders.size();
		client.process(unassignedOrders, allocationsMap);
		// 处理过的出库单会从列表里移除; 数量没变说明一个波次都没创建, 提示用户原因
		if (unassignedOrders.size() == beforeCount) {
			throw new JeecgBootBizTipException("没有出库单符合所选波次策略的条件(例如: 同一商品的出库单数量没有达到策略要求的最小出库单数)");
		}
	}
//	public void updatePickStatus(String waveId){
//		WmsWaveMaster wave = getById(waveId);
//		if (wave == null) {
//			//抛出异常
//			throw new JeecgBootException("波次不存在");
//		}
//		List<WmsOutOrders> orders = wmsOutOrdersService.selectByWaveId(waveId);
//		if (orders.isEmpty()) {
//			return;
//		}
//		boolean allPicked = orders.stream()
//				.allMatch(order -> WarehouseDictEnum.OUTBOUND_PICKED.getCode().equals(order.getStatus()));
//		if(allPicked){
//			wave.setStatus(WarehouseDictEnum.WAVE_PICKED.getCode());
//			updateById(wave);
//		}
//
//	}


	private String generateWaveNo(String strategyType) {
		return "WAVE-" + strategyType + "-" + System.currentTimeMillis();
	}

}
