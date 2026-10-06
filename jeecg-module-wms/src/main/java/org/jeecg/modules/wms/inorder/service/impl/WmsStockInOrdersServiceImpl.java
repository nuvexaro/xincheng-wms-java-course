package org.jeecg.modules.wms.inorder.service.impl;

import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.util.RedisUtil;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.modules.wms.config.WarehouseDictEnum;
import org.jeecg.modules.wms.goods.entity.WmsCargoOwners;
import org.jeecg.modules.wms.goods.service.IWmsCargoOwnersService;
import org.jeecg.modules.wms.inorder.entity.WmsStockInOrders;
import org.jeecg.modules.wms.inorder.entity.WmsStockInOrderItems;
import org.jeecg.modules.wms.inorder.mapper.WmsStockInOrderItemsMapper;
import org.jeecg.modules.wms.inorder.mapper.WmsStockInOrdersMapper;
import org.jeecg.modules.wms.inorder.service.IWmsStockInOrdersService;
import org.jeecg.modules.wms.warehouse.entity.WmsWarehouses;
import org.jeecg.modules.wms.warehouse.service.IWmsWarehousesService;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @Description: 入库单主表
 * @Author: jeecg-boot
 * @Date:   2026-10-06
 * @Version: V1.0
 */
@Slf4j
@Service
public class WmsStockInOrdersServiceImpl extends ServiceImpl<WmsStockInOrdersMapper, WmsStockInOrders> implements IWmsStockInOrdersService {

	/** 入库单号前缀 */
	private static final String ORDER_NUMBER_PREFIX = "ASN";
	/** 入库单号序号在 redis 中的 key 前缀, 后面拼 yyyyMMdd, 一天一个 key */
	private static final String ORDER_NUMBER_KEY_PREFIX = "wms:stockInOrders:orderNumber:";
	/** key 的有效期(秒): 比 24 小时多 60 秒, 防止跨零点瞬间因网络延迟导致 key 刚过期又被重新创建, 生成重复序号 */
	private static final long ORDER_NUMBER_KEY_EXPIRE_SECONDS = 24 * 60 * 60 + 60L;
	/** 日期格式: 年月日 */
	private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

	@Autowired
	private WmsStockInOrdersMapper wmsStockInOrdersMapper;
	@Autowired
	private WmsStockInOrderItemsMapper wmsStockInOrderItemsMapper;
	@Autowired
	private IWmsCargoOwnersService wmsCargoOwnersService;
	@Autowired
	private IWmsWarehousesService wmsWarehousesService;
	@Autowired
	private RedisUtil redisUtil;

	// ============================== 查询 ==============================

	/**
	 * 分页查询入库单, 并补全货主名称、仓库名称(入库单表里只存了货主id、仓库id)
	 */
	@Override
	public IPage<WmsStockInOrders> pageList(Page<WmsStockInOrders> page, Wrapper<WmsStockInOrders> queryWrapper) {
		IPage<WmsStockInOrders> pageList = page(page, queryWrapper);
		fillOwnerAndWarehouseName(pageList.getRecords());
		return pageList;
	}

	/**
	 * 批量补全货主名称、仓库名称: 先收集当前页用到的id, 各查一次数据库, 再回填
	 */
	private void fillOwnerAndWarehouseName(List<WmsStockInOrders> records) {
		if (records == null || records.isEmpty()) {
			return;
		}
		Set<String> ownerIds = new HashSet<>();
		Set<String> warehouseIds = new HashSet<>();
		for (WmsStockInOrders record : records) {
			if (oConvertUtils.isNotEmpty(record.getOwnerId())) {
				ownerIds.add(record.getOwnerId());
			}
			if (oConvertUtils.isNotEmpty(record.getWarehouseId())) {
				warehouseIds.add(record.getWarehouseId());
			}
		}
		// map<货主id, 货主名称>
		Map<String, String> ownerNameMap = new HashMap<>();
		if (!ownerIds.isEmpty()) {
			for (WmsCargoOwners owner : wmsCargoOwnersService.listByIds(ownerIds)) {
				ownerNameMap.put(owner.getId(), owner.getOwnerName());
			}
		}
		// map<仓库id, 仓库名称>
		Map<String, String> warehouseNameMap = new HashMap<>();
		if (!warehouseIds.isEmpty()) {
			for (WmsWarehouses warehouse : wmsWarehousesService.listByIds(warehouseIds)) {
				warehouseNameMap.put(warehouse.getId(), warehouse.getWarehouseName());
			}
		}
		for (WmsStockInOrders record : records) {
			record.setOwnerName(ownerNameMap.get(record.getOwnerId()));
			record.setWarehouseName(warehouseNameMap.get(record.getWarehouseId()));
		}
	}

	/**
	 * 查询入库单并加行锁
	 */
	@Override
	public WmsStockInOrders getByIdForUpdate(String id) {
		if (oConvertUtils.isEmpty(id)) {
			return null;
		}
		return wmsStockInOrdersMapper.selectOne(new LambdaQueryWrapper<WmsStockInOrders>()
				.eq(WmsStockInOrders::getId, id)
				.last("FOR UPDATE"));
	}

	// ============================== 新增 ==============================

	/**
	 * 创建入库单: 只向入库单主表添加一条记录, 入库明细在"编辑"里添加
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public void add(WmsStockInOrders wmsStockInOrders) {
		//1.必填项校验: 入库类型、货主、仓库
		if (wmsStockInOrders == null
				|| oConvertUtils.isEmpty(wmsStockInOrders.getOrderType())
				|| oConvertUtils.isEmpty(wmsStockInOrders.getOwnerId())
				|| oConvertUtils.isEmpty(wmsStockInOrders.getWarehouseId())) {
			throw new JeecgBootException("入库类型、货主、仓库不能为空");
		}
		//2.主键置空, 由 mybatis-plus 自动生成(前端新增表单会带一个空的 id)
		wmsStockInOrders.setId(null);
		//3.生成入库单号
		wmsStockInOrders.setOrderNumber(generateOrderNo());
		//4.状态默认为初始
		wmsStockInOrders.setStatus(WarehouseDictEnum.INBOUND_INITIAL.getCode());
		//5.数量默认为0
		wmsStockInOrders.setTotalExpectedQuantity(0);
		wmsStockInOrders.setTotalReceivedQuantity(0);
		wmsStockInOrders.setTotalShelvedQuantity(0);
		wmsStockInOrders.setTotalDefectiveQuantity(0);
		//6.添加到入库单表
		save(wmsStockInOrders);
	}

	/**
	 * 生成入库单号: ASN + 8位年月日 + 4位序号, 例如 ASN202610060001
	 * 序号使用 redis 的自增实现, key 按天区分, 第二天序号重新从 1 开始
	 */
	private String generateOrderNo() {
		String yyyyMMdd = LocalDate.now().format(DATE_FORMATTER);
		String key = ORDER_NUMBER_KEY_PREFIX + yyyyMMdd;
		long incr;
		try {
			incr = redisUtil.incr(key, 1);
			if (incr == 1) {
				// 当天第一次生成时设置有效期, key 过了当天就没用了, 不要让它长期占用内存
				redisUtil.expire(key, ORDER_NUMBER_KEY_EXPIRE_SECONDS);
			}
		} catch (Exception e) {
			log.error("生成入库单号失败", e);
			throw new JeecgBootException("生成入库单号失败");
		}
		return String.format("%s%s%04d", ORDER_NUMBER_PREFIX, yyyyMMdd, incr);
	}

	/**
	 * 添加一对多(excel导入时使用): 没有入库单号时自动生成, 没有状态时默认为初始
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public void saveMain(WmsStockInOrders wmsStockInOrders, List<WmsStockInOrderItems> wmsStockInOrderItemsList) {
		if (oConvertUtils.isEmpty(wmsStockInOrders.getOrderNumber())) {
			wmsStockInOrders.setOrderNumber(generateOrderNo());
		}
		if (oConvertUtils.isEmpty(wmsStockInOrders.getStatus())) {
			wmsStockInOrders.setStatus(WarehouseDictEnum.INBOUND_INITIAL.getCode());
		}
		wmsStockInOrdersMapper.insert(wmsStockInOrders);
		if(wmsStockInOrderItemsList!=null && wmsStockInOrderItemsList.size()>0) {
			for(WmsStockInOrderItems entity:wmsStockInOrderItemsList) {
				//外键设置
				entity.setOrderId(wmsStockInOrders.getId());
				wmsStockInOrderItemsMapper.insert(entity);
			}
		}
	}

	// ============================== 修改 ==============================

	/**
	 * 修改入库单: 入库单修改数据, 入库明细采用先删除再添加的方式
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public void updateMain(WmsStockInOrders wmsStockInOrders,List<WmsStockInOrderItems> wmsStockInOrderItemsList) {
		//==========1.条件判断处理==========
		//1.1 入库单必须存在
		if (wmsStockInOrders == null || oConvertUtils.isEmpty(wmsStockInOrders.getId())) {
			throw new JeecgBootException("入库单id不能为空");
		}
		String orderId = wmsStockInOrders.getId();
		WmsStockInOrders originOrder = wmsStockInOrdersMapper.selectById(orderId);
		if (originOrder == null) {
			throw new JeecgBootException("未找到对应数据");
		}
		//1.2 只有初始状态、审核失败状态的入库单可以修改(状态以数据库里的为准, 不能用前端传过来的)
		String status = originOrder.getStatus();
		if (!WarehouseDictEnum.INBOUND_INITIAL.getCode().equals(status)
				&& !WarehouseDictEnum.INBOUND_REJECTED.getCode().equals(status)) {
			throw new JeecgBootException("只有初始状态、审核失败状态的入库单才可以修改");
		}
		//1.3 入库类型、仓库不能改成空
		if (oConvertUtils.isEmpty(wmsStockInOrders.getOrderType()) || oConvertUtils.isEmpty(wmsStockInOrders.getWarehouseId())) {
			throw new JeecgBootException("入库类型、仓库不能为空");
		}
		//1.4 入库单明细至少添加一条
		if (wmsStockInOrderItemsList == null || wmsStockInOrderItemsList.isEmpty()) {
			throw new JeecgBootException("入库单明细至少添加一条");
		}

		//==========2.入库明细的操作==========
		//2.1 同一个入库单的明细中商品唯一: 同一个商品数量累加(合并), 只保留一条明细
		//    map<商品id, 明细对象>, 用 LinkedHashMap 保持页面上的顺序
		Map<String, WmsStockInOrderItems> productItemMap = new LinkedHashMap<>();
		//    预期入库总量, 遍历时累加
		int totalExpectedQuantity = 0;
		for (WmsStockInOrderItems item : wmsStockInOrderItemsList) {
			if (oConvertUtils.isEmpty(item.getProductId())) {
				throw new JeecgBootException("入库单明细的商品不能为空");
			}
			int expectedQuantity = item.getExpectedQuantity() == null ? 0 : item.getExpectedQuantity();
			if (expectedQuantity <= 0) {
				throw new JeecgBootException("入库单明细的采购数量必须大于0");
			}
			WmsStockInOrderItems existItem = productItemMap.get(item.getProductId());
			if (existItem == null) {
				// map中不存在: 重新构造一个只包含允许保存字段的明细对象, 不直接用前端传过来的对象
				WmsStockInOrderItems newItem = new WmsStockInOrderItems();
				newItem.setOrderId(orderId);
				newItem.setProductId(item.getProductId());
				newItem.setExpectedQuantity(expectedQuantity);
				newItem.setReceivedQuantity(0);
				newItem.setShelvedQuantity(0);
				newItem.setDefectiveQuantity(0);
				newItem.setRemarks(item.getRemarks());
				newItem.setStatus(WarehouseDictEnum.INBOUND_DETAIL_INITIAL.getCode());
				productItemMap.put(item.getProductId(), newItem);
			} else {
				// map中已存在: 累加数量
				existItem.setExpectedQuantity(existItem.getExpectedQuantity() + expectedQuantity);
			}
			// 累加每个商品的采购数量
			totalExpectedQuantity += expectedQuantity;
		}
		//2.2 先删: delete from wms_stock_in_order_items where order_id = 入库单id
		wmsStockInOrderItemsMapper.deleteByMainId(orderId);
		//2.3 再添加合并后的明细
		for (WmsStockInOrderItems newItem : productItemMap.values()) {
			wmsStockInOrderItemsMapper.insert(newItem);
		}

		//==========3.更新入库单信息==========
		//3.1 不直接使用前端传过来的对象更新: updateById 只要属性值不为空就会参与 update set,
		//    前端对象可能携带不该被修改的字段(如 status、orderNumber、totalReceivedQuantity 等), 存在安全问题。
		//    所以重新构造一个只包含允许修改字段的对象, 其余字段保持 null, 就不会被更新。
		WmsStockInOrders updateOrder = new WmsStockInOrders();
		updateOrder.setId(orderId);
		//3.2 只更新: 入库类型、来源单号、仓库、预计到货时间、备注、预期入库总量
		updateOrder.setOrderType(wmsStockInOrders.getOrderType());
		updateOrder.setSourceNumber(wmsStockInOrders.getSourceNumber());
		updateOrder.setWarehouseId(wmsStockInOrders.getWarehouseId());
		updateOrder.setExpectedArrivalTime(wmsStockInOrders.getExpectedArrivalTime());
		updateOrder.setRemarks(wmsStockInOrders.getRemarks());
		updateOrder.setTotalExpectedQuantity(totalExpectedQuantity);
		//3.3 更新入库单
		wmsStockInOrdersMapper.updateById(updateOrder);
	}

	// ============================== 删除 ==============================

	/**
	 * 删除入库单: 初始状态直接删除; 审核失败状态更新为已作废; 其它状态不允许删除
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public void delMain(String id) {
		if (oConvertUtils.isEmpty(id)) {
			throw new JeecgBootException("入库单id不能为空");
		}
		WmsStockInOrders order = wmsStockInOrdersMapper.selectById(id);
		if (order == null) {
			throw new JeecgBootException("未找到对应数据");
		}
		String status = order.getStatus();
		if (WarehouseDictEnum.INBOUND_INITIAL.getCode().equals(status)) {
			// 初始状态: 直接删除入库明细和入库单
			wmsStockInOrderItemsMapper.deleteByMainId(id);
			wmsStockInOrdersMapper.deleteById(id);
		} else if (WarehouseDictEnum.INBOUND_REJECTED.getCode().equals(status)) {
			// 审核失败状态: 不删除数据, 更新为已作废
			changeStatus(order, WarehouseDictEnum.INBOUND_CANCELED.getCode());
			WmsStockInOrderItems itemUpdate = new WmsStockInOrderItems();
			itemUpdate.setStatus(WarehouseDictEnum.INBOUND_DETAIL_CANCELED.getCode());
			wmsStockInOrderItemsMapper.update(itemUpdate, new LambdaUpdateWrapper<WmsStockInOrderItems>()
					.eq(WmsStockInOrderItems::getOrderId, id));
		} else {
			throw new JeecgBootException("入库单" + order.getOrderNumber() + "不是初始状态或审核失败状态, 不能删除");
		}
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void delBatchMain(Collection<? extends Serializable> idList) {
		if (idList == null) {
			return;
		}
		for(Serializable id:idList) {
			if (id != null && oConvertUtils.isNotEmpty(id.toString())) {
				delMain(id.toString().trim());
			}
		}
	}

	// ============================== 审核 ==============================

	/**
	 * 提交审核: 初始状态、审核失败状态 -> 提交审核
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public void submitAudit(String id) {
		if (oConvertUtils.isEmpty(id)) {
			throw new JeecgBootException("请选择要提交审核的入库单");
		}
		WmsStockInOrders order = wmsStockInOrdersMapper.selectById(id);
		if (order == null) {
			throw new JeecgBootException("未找到对应数据");
		}
		String status = order.getStatus();
		if (!WarehouseDictEnum.INBOUND_INITIAL.getCode().equals(status)
				&& !WarehouseDictEnum.INBOUND_REJECTED.getCode().equals(status)) {
			throw new JeecgBootException("只有初始状态、审核失败状态的入库单才可以提交审核");
		}
		// 没有入库明细的入库单提交审核没有意义(后面要根据明细创建收货任务)
		List<WmsStockInOrderItems> itemList = wmsStockInOrderItemsMapper.selectByMainId(id);
		if (itemList == null || itemList.isEmpty()) {
			throw new JeecgBootException("入库单" + order.getOrderNumber() + "还没有入库明细, 请先点击编辑添加商品, 再提交审核");
		}
		changeStatus(order, WarehouseDictEnum.INBOUND_SUBMIT_AUDIT.getCode());
	}

	/**
	 * 审核入库单: 提交审核状态 -> 审核通过 / 审核失败
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public void audit(String id, String status) {
		if (oConvertUtils.isEmpty(id)) {
			throw new JeecgBootException("请选择要审核的入库单");
		}
		// 审核结果只能是审核通过或审核失败
		if (!WarehouseDictEnum.INBOUND_APPROVED.getCode().equals(status)
				&& !WarehouseDictEnum.INBOUND_REJECTED.getCode().equals(status)) {
			throw new JeecgBootException("请选择审核结果: 审核通过 或 不通过");
		}
		WmsStockInOrders order = wmsStockInOrdersMapper.selectById(id);
		if (order == null) {
			throw new JeecgBootException("未找到对应数据");
		}
		if (!WarehouseDictEnum.INBOUND_SUBMIT_AUDIT.getCode().equals(order.getStatus())) {
			throw new JeecgBootException("只有提交审核状态的入库单才可以审核");
		}
		// 审核通过的入库单下必须有入库明细; 审核失败可以没有
		if (WarehouseDictEnum.INBOUND_APPROVED.getCode().equals(status)) {
			List<WmsStockInOrderItems> itemList = wmsStockInOrderItemsMapper.selectByMainId(id);
			if (itemList == null || itemList.isEmpty()) {
				throw new JeecgBootException("入库单" + order.getOrderNumber() + "没有入库明细, 不能审核通过");
			}
		}
		changeStatus(order, status);
	}

	/**
	 * 修改入库单状态
	 * where 条件带上"修改前的状态": 如果这期间状态被别人改了, 影响行数为 0, 直接报错, 避免并发时状态被改乱
	 *
	 * @param order     从数据库查出来的入库单(里面是修改前的状态)
	 * @param newStatus 新状态
	 */
	private void changeStatus(WmsStockInOrders order, String newStatus) {
		WmsStockInOrders updateOrder = new WmsStockInOrders();
		updateOrder.setStatus(newStatus);
		boolean updated = update(updateOrder, new LambdaUpdateWrapper<WmsStockInOrders>()
				.eq(WmsStockInOrders::getId, order.getId())
				.eq(WmsStockInOrders::getStatus, order.getStatus()));
		if (!updated) {
			throw new JeecgBootException("入库单" + order.getOrderNumber() + "状态已变更, 请刷新后重试");
		}
	}

	// ============================== 收货 ==============================

	/**
	 * 收货后更新入库单的实际收货总量、不良品总数量和状态
	 *
	 * @param stockInOrderId 入库单id
	 * @return true: 入库单已收货完成
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean updateReceivedStatus(String stockInOrderId) {
		//1.收货中(或审核通过)状态的入库单方可更新收货状态
		if (oConvertUtils.isEmpty(stockInOrderId)) {
			throw new JeecgBootException("入库单id不能为空");
		}
		WmsStockInOrders stockInOrders = wmsStockInOrdersMapper.selectById(stockInOrderId);
		if (stockInOrders == null) {
			throw new JeecgBootException("入库单不存在");
		}
		if (!WarehouseDictEnum.INBOUND_RECEIVING.getCode().equals(stockInOrders.getStatus())
				&& !WarehouseDictEnum.INBOUND_APPROVED.getCode().equals(stockInOrders.getStatus())) {
			throw new JeecgBootException("入库单" + stockInOrders.getOrderNumber() + "不是收货中状态, 不允许更新收货状态");
		}

		//2.根据入库单id查询入库单明细列表
		List<WmsStockInOrderItems> itemsList = wmsStockInOrderItemsMapper.selectByMainId(stockInOrderId);
		if (itemsList == null || itemsList.isEmpty()) {
			throw new JeecgBootException("入库单" + stockInOrders.getOrderNumber() + "没有入库明细");
		}

		//3.计算采购总数、良品总数、不良品总数, 并判断是否所有明细都收货完成
		int expectedCount = 0;
		int goodCount = 0;
		int badCount = 0;
		boolean isCompleted = true;
		for (WmsStockInOrderItems item : itemsList) {
			expectedCount += item.getExpectedQuantity() == null ? 0 : item.getExpectedQuantity();
			goodCount += item.getReceivedQuantity() == null ? 0 : item.getReceivedQuantity();
			badCount += item.getDefectiveQuantity() == null ? 0 : item.getDefectiveQuantity();
			if (!WarehouseDictEnum.INBOUND_DETAIL_RECEIVED.getCode().equals(item.getStatus())) {
				isCompleted = false;
			}
		}
		//  数量完整性校验: 采购数量必须 >= 良品 + 不良品
		if (goodCount + badCount > expectedCount) {
			throw new JeecgBootException("收货数量不能大于采购数量");
		}

		//4.更新入库单: 只设置需要更新的字段
		WmsStockInOrders updateOrder = new WmsStockInOrders();
		updateOrder.setId(stockInOrderId);
		updateOrder.setTotalReceivedQuantity(goodCount);
		updateOrder.setTotalDefectiveQuantity(badCount);
		//  入库单明细全部收货完成, 则入库单状态为收货完成, 否则为收货中
		updateOrder.setStatus(isCompleted
				? WarehouseDictEnum.INBOUND_RECEIVED.getCode()
				: WarehouseDictEnum.INBOUND_RECEIVING.getCode());
		boolean updated = updateById(updateOrder);
		if (!updated) {
			throw new JeecgBootException("更新入库单失败");
		}
		return isCompleted;
	}
}
