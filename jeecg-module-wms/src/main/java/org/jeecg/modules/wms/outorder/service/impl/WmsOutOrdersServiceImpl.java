package org.jeecg.modules.wms.outorder.service.impl;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jeecg.common.constant.ProvinceCityArea;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.util.RedisUtil;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.modules.wms.config.WarehouseDictEnum;
import org.jeecg.modules.wms.goods.entity.WmsCargoOwners;
import org.jeecg.modules.wms.goods.service.IWmsCargoOwnersService;
import org.jeecg.modules.wms.inventory.entity.WmsInventory;
import org.jeecg.modules.wms.inventory.service.IWmsInventoryService;
import org.jeecg.modules.wms.outorder.entity.WmsOutOrders;
import org.jeecg.modules.wms.outorder.entity.WmsOutOrdersAllocation;
import org.jeecg.modules.wms.outorder.entity.WmsOutOrdersItems;
import org.jeecg.modules.wms.outorder.mapper.WmsOutOrdersAllocationMapper;
import org.jeecg.modules.wms.outorder.mapper.WmsOutOrdersItemsMapper;
import org.jeecg.modules.wms.outorder.mapper.WmsOutOrdersMapper;
import org.jeecg.modules.wms.outorder.service.IWmsOutOrdersService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;

/**
 * @Description: 出库单主表
 * @Version: V1.0
 */
@Slf4j
@Service
public class WmsOutOrdersServiceImpl extends ServiceImpl<WmsOutOrdersMapper, WmsOutOrders> implements IWmsOutOrdersService {

    /** 出库单号序号在 redis 中的 key 前缀, 后面拼 yyyyMMdd, 一天一个 key */
    private static final String ORDER_NO_KEY_PREFIX = "wms:outOrders:orderNo:";
    /** key 的有效期(秒): 比 24 小时多 60 秒, 防止跨零点瞬间 key 刚过期又被重新创建, 生成重复序号 */
    private static final long ORDER_NO_KEY_EXPIRE_SECONDS = 24 * 60 * 60 + 60L;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Autowired
    private WmsOutOrdersItemsMapper wmsOutOrdersItemsMapper;
    @Autowired
    private WmsOutOrdersAllocationMapper wmsOutOrdersAllocationMapper;
    @Autowired
    private IWmsCargoOwnersService wmsCargoOwnersService;
    @Autowired
    private IWmsInventoryService wmsInventoryService;
    @Autowired
    private RedisUtil redisUtil;
    /** 省市区工具: 根据区县编码查出"省/市/区"的名称 */
    @Autowired
    private ProvinceCityArea provinceCityArea;

    // ============================== 查询 ==============================

    @Override
    public IPage<WmsOutOrders> pageList(Page<WmsOutOrders> page, Wrapper<WmsOutOrders> queryWrapper) {
        IPage<WmsOutOrders> pageList = page(page, queryWrapper);
        List<WmsOutOrders> records = pageList.getRecords();
        if (records == null || records.isEmpty()) {
            return pageList;
        }
        // 出库单表里只存了货主id, 列表要显示货主名称: 收集当前页用到的货主id, 查一次数据库, 再回填
        Set<String> ownerIds = new HashSet<>();
        for (WmsOutOrders record : records) {
            if (oConvertUtils.isNotEmpty(record.getOwnerId())) {
                ownerIds.add(record.getOwnerId());
            }
        }
        Map<String, String> ownerNameMap = new HashMap<>();
        if (!ownerIds.isEmpty()) {
            for (WmsCargoOwners owner : wmsCargoOwnersService.listByIds(ownerIds)) {
                ownerNameMap.put(owner.getId(), owner.getOwnerName());
            }
        }
        for (WmsOutOrders record : records) {
            record.setOwnerName(ownerNameMap.get(record.getOwnerId()));
        }
        return pageList;
    }

    @Override
    public List<WmsOutOrdersItems> selectItemsByMainId(String orderId) {
        return wmsOutOrdersItemsMapper.selectByMainId(orderId);
    }

    @Override
    public List<WmsOutOrdersAllocation> selectAllocationByMainId(String orderId) {
        return wmsOutOrdersAllocationMapper.selectByMainId(orderId);
    }

    // ============================== 新增 ==============================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void add(WmsOutOrders wmsOutOrders) {
        //1.必填项校验: 出库类型、订单来源、货主
        if (wmsOutOrders == null || oConvertUtils.isEmpty(wmsOutOrders.getOrderType())
                || oConvertUtils.isEmpty(wmsOutOrders.getOrderSource()) || oConvertUtils.isEmpty(wmsOutOrders.getOwnerId())) {
            throw new JeecgBootException("出库类型、订单来源、货主不能为空");
        }
        //2.主键置空, 由 mybatis-plus 自动生成
        wmsOutOrders.setId(null);
        //3.生成出库单号: 8位年月日 + 4位序号
        wmsOutOrders.setOrderNo(generateOrderNo());
        //4.状态默认为已创建; 数量默认为0; 还没创建运单
        wmsOutOrders.setStatus(WarehouseDictEnum.OUTBOUND_CREATED.getCode());
        wmsOutOrders.setTotalQuantity(0);
        wmsOutOrders.setTotalSku(0);
        wmsOutOrders.setCreatedWaybill("0");
        wmsOutOrders.setWaveId(null);
        //5.根据省市区编码填上省、市、区的名称(后面生成运单时要用)
        fillRegionNames(wmsOutOrders);
        save(wmsOutOrders);
    }

    /**
     * 生成出库单号: 8位年月日 + 4位序号, 例如 202610080001
     * 序号使用 redis 的自增实现, key 按天区分, 第二天序号重新从 1 开始
     */
    private String generateOrderNo() {
        String yyyyMMdd = LocalDate.now().format(DATE_FORMATTER);
        String key = ORDER_NO_KEY_PREFIX + yyyyMMdd;
        long incr;
        try {
            incr = redisUtil.incr(key, 1);
            if (incr == 1) {
                redisUtil.expire(key, ORDER_NO_KEY_EXPIRE_SECONDS);
            }
        } catch (Exception e) {
            log.error("生成出库单号失败", e);
            throw new JeecgBootException("生成出库单号失败");
        }
        return String.format("%s%04d", yyyyMMdd, incr);
    }

    /**
     * 根据省市区编码(region)填上省、市、区县的名称
     * provinceCityArea.getText 返回的格式是 "省/市/区", 例如 "北京市/市辖区/东城区"
     */
    private void fillRegionNames(WmsOutOrders wmsOutOrders) {
        if (oConvertUtils.isEmpty(wmsOutOrders.getRegion())) {
            return;
        }
        try {
            // 前端省市区组件可能传 "省编码,市编码,区编码" 这种格式, 取最后一段(区县编码)去查
            String region = wmsOutOrders.getRegion().trim();
            String code = region.contains(",") ? region.substring(region.lastIndexOf(",") + 1).trim() : region;
            String text = provinceCityArea.getText(code);
            if (oConvertUtils.isEmpty(text)) {
                return;
            }
            String[] names = text.split("/");
            if (names.length > 0) {
                wmsOutOrders.setShippingProvince(names[0]);
            }
            if (names.length > 1) {
                wmsOutOrders.setShippingCity(names[1]);
            }
            if (names.length > 2) {
                wmsOutOrders.setShippingCounty(names[2]);
            }
        } catch (Exception e) {
            // 省市区名称只是辅助信息, 解析失败不影响保存出库单
            log.warn("解析省市区编码失败: {}", wmsOutOrders.getRegion(), e);
        }
    }

    // ============================== 修改 ==============================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateMain(WmsOutOrders wmsOutOrders, List<WmsOutOrdersItems> itemsList) {
        //==========1.条件判断==========
        if (wmsOutOrders == null || oConvertUtils.isEmpty(wmsOutOrders.getId())) {
            throw new JeecgBootException("出库单id不能为空");
        }
        String orderId = wmsOutOrders.getId();
        WmsOutOrders originOrder = getById(orderId);
        if (originOrder == null) {
            throw new JeecgBootException("未找到对应数据");
        }
        // 只有已创建、审核失败状态的出库单可以修改(状态以数据库里的为准)
        String status = originOrder.getStatus();
        if (!WarehouseDictEnum.OUTBOUND_CREATED.getCode().equals(status)
                && !WarehouseDictEnum.OUTBOUND_REJECTED.getCode().equals(status)) {
            throw new JeecgBootException("只有已创建、审核失败状态的出库单才可以修改");
        }
        if (oConvertUtils.isEmpty(wmsOutOrders.getOrderType()) || oConvertUtils.isEmpty(wmsOutOrders.getOrderSource())) {
            throw new JeecgBootException("出库类型、订单来源不能为空");
        }
        if (itemsList == null || itemsList.isEmpty()) {
            throw new JeecgBootException("出库单明细至少添加一条");
        }

        //==========2.出库单明细: 同一个商品、同一个批次号合并成一条, 数量累加==========
        Map<String, WmsOutOrdersItems> itemMap = new LinkedHashMap<>();
        Set<String> skuIds = new HashSet<>();
        int totalQuantity = 0;
        for (WmsOutOrdersItems item : itemsList) {
            if (oConvertUtils.isEmpty(item.getSkuId())) {
                throw new JeecgBootException("出库单明细的商品不能为空");
            }
            int expectedQuantity = item.getExpectedQuantity() == null ? 0 : item.getExpectedQuantity();
            if (expectedQuantity <= 0) {
                throw new JeecgBootException("出库单明细的预期出库数量必须大于0");
            }
            String batchNumber = oConvertUtils.isEmpty(item.getBatchNumber()) ? null : item.getBatchNumber().trim();
            String key = item.getSkuId() + "#" + (batchNumber == null ? "" : batchNumber);
            WmsOutOrdersItems existItem = itemMap.get(key);
            if (existItem == null) {
                // 重新构造一个只包含允许保存字段的明细对象, 不直接用前端传过来的对象
                WmsOutOrdersItems newItem = new WmsOutOrdersItems();
                newItem.setOrderId(orderId);
                newItem.setSkuId(item.getSkuId());
                newItem.setExpectedQuantity(expectedQuantity);
                newItem.setAllocatedQuantity(0);
                newItem.setPickedQuantity(0);
                newItem.setPackedQuantity(0);
                newItem.setBatchNumber(batchNumber);
                newItem.setExpiryDate(item.getExpiryDate());
                newItem.setStatus(WarehouseDictEnum.OUTBOUND_DETAIL_CREATED.getCode());
                itemMap.put(key, newItem);
            } else {
                existItem.setExpectedQuantity(existItem.getExpectedQuantity() + expectedQuantity);
            }
            skuIds.add(item.getSkuId());
            totalQuantity += expectedQuantity;
        }
        // 先删后插
        wmsOutOrdersItemsMapper.deleteByMainId(orderId);
        for (WmsOutOrdersItems newItem : itemMap.values()) {
            wmsOutOrdersItemsMapper.insert(newItem);
        }

        //==========3.更新出库单: 只更新允许修改的字段==========
        WmsOutOrders updateOrder = new WmsOutOrders();
        updateOrder.setId(orderId);
        updateOrder.setOrderType(wmsOutOrders.getOrderType());
        updateOrder.setOrderSource(wmsOutOrders.getOrderSource());
        updateOrder.setOrderSourceNo(wmsOutOrders.getOrderSourceNo());
        updateOrder.setWarehouseId(wmsOutOrders.getWarehouseId());
        updateOrder.setExpectedShipTime(wmsOutOrders.getExpectedShipTime());
        updateOrder.setConsignee(wmsOutOrders.getConsignee());
        updateOrder.setRegion(wmsOutOrders.getRegion());
        updateOrder.setShippingAddress(wmsOutOrders.getShippingAddress());
        updateOrder.setContact(wmsOutOrders.getContact());
        updateOrder.setRemark(wmsOutOrders.getRemark());
        // 总商品数量、总sku种类数
        updateOrder.setTotalQuantity(totalQuantity);
        updateOrder.setTotalSku(skuIds.size());
        fillRegionNames(updateOrder);
        updateById(updateOrder);
    }

    // ============================== 删除 ==============================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delMain(String id) {
        if (oConvertUtils.isEmpty(id)) {
            throw new JeecgBootException("出库单id不能为空");
        }
        WmsOutOrders order = getById(id);
        if (order == null) {
            throw new JeecgBootException("未找到对应数据");
        }
        String status = order.getStatus();
        if (WarehouseDictEnum.OUTBOUND_CREATED.getCode().equals(status)) {
            // 已创建状态: 直接删除明细和出库单
            wmsOutOrdersItemsMapper.deleteByMainId(id);
            removeById(id);
        } else if (WarehouseDictEnum.OUTBOUND_REJECTED.getCode().equals(status)) {
            // 审核失败状态: 不删除数据, 更新为已取消
            changeStatus(order, WarehouseDictEnum.OUTBOUND_CANCELED.getCode());
            WmsOutOrdersItems itemUpdate = new WmsOutOrdersItems();
            itemUpdate.setStatus(WarehouseDictEnum.OUTBOUND_DETAIL_CANCELED.getCode());
            wmsOutOrdersItemsMapper.update(itemUpdate, new LambdaUpdateWrapper<WmsOutOrdersItems>()
                    .eq(WmsOutOrdersItems::getOrderId, id));
        } else {
            throw new JeecgBootException("出库单" + order.getOrderNo() + "不是已创建或审核失败状态, 不能删除");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delBatchMain(String ids) {
        for (String id : splitIds(ids)) {
            delMain(id);
        }
    }

    // ============================== 审核 ==============================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitAudit(String id) {
        if (oConvertUtils.isEmpty(id)) {
            throw new JeecgBootException("请选择要提交审核的出库单");
        }
        WmsOutOrders order = getById(id);
        if (order == null) {
            throw new JeecgBootException("未找到对应数据");
        }
        String status = order.getStatus();
        if (!WarehouseDictEnum.OUTBOUND_CREATED.getCode().equals(status)
                && !WarehouseDictEnum.OUTBOUND_REJECTED.getCode().equals(status)) {
            throw new JeecgBootException("只有已创建、审核失败状态的出库单才可以提交审核");
        }
        List<WmsOutOrdersItems> itemsList = wmsOutOrdersItemsMapper.selectByMainId(id);
        if (itemsList == null || itemsList.isEmpty()) {
            throw new JeecgBootException("出库单" + order.getOrderNo() + "还没有出库明细, 请先点击编辑添加商品, 再提交审核");
        }
        changeStatus(order, WarehouseDictEnum.OUTBOUND_SUBMIT_AUDIT.getCode());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(String id, String status) {
        if (oConvertUtils.isEmpty(id)) {
            throw new JeecgBootException("请选择要审核的出库单");
        }
        if (!WarehouseDictEnum.OUTBOUND_APPROVED.getCode().equals(status)
                && !WarehouseDictEnum.OUTBOUND_REJECTED.getCode().equals(status)) {
            throw new JeecgBootException("请选择审核结果: 审核通过 或 不通过");
        }
        WmsOutOrders order = getById(id);
        if (order == null) {
            throw new JeecgBootException("未找到对应数据");
        }
        if (!WarehouseDictEnum.OUTBOUND_SUBMIT_AUDIT.getCode().equals(order.getStatus())) {
            throw new JeecgBootException("只有提交审核状态的出库单才可以审核");
        }
        if (WarehouseDictEnum.OUTBOUND_APPROVED.getCode().equals(status)) {
            List<WmsOutOrdersItems> itemsList = wmsOutOrdersItemsMapper.selectByMainId(id);
            if (itemsList == null || itemsList.isEmpty()) {
                throw new JeecgBootException("出库单" + order.getOrderNo() + "没有出库明细, 不能审核通过");
            }
        }
        changeStatus(order, status);
    }

    /**
     * 修改出库单状态: where 条件带上"修改前的状态", 这期间状态被别人改了就更新不到, 直接报错
     */
    private void changeStatus(WmsOutOrders order, String newStatus) {
        WmsOutOrders updateOrder = new WmsOutOrders();
        updateOrder.setStatus(newStatus);
        boolean updated = update(updateOrder, new LambdaUpdateWrapper<WmsOutOrders>()
                .eq(WmsOutOrders::getId, order.getId())
                .eq(WmsOutOrders::getStatus, order.getStatus()));
        if (!updated) {
            throw new JeecgBootException("出库单" + order.getOrderNo() + "状态已变更, 请刷新后重试");
        }
    }

    // ============================== 分配库存 ==============================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String allocateStock(String ids) {
        List<String> idList = splitIds(ids);
        if (idList.isEmpty()) {
            throw new JeecgBootException("请选择要分配库存的出库单");
        }
        //1. 先把所有出库单校验一遍, 有一个不符合就直接报错, 什么都不改
        List<WmsOutOrders> orderList = new ArrayList<>();
        for (String id : idList) {
            WmsOutOrders order = getById(id);
            if (order == null) {
                throw new JeecgBootException("出库单不存在: " + id);
            }
            // 出库单状态为审核通过或分配失败才允许分配库存
            if (!WarehouseDictEnum.OUTBOUND_APPROVED.getCode().equals(order.getStatus())
                    && !WarehouseDictEnum.OUTBOUND_FAILED.getCode().equals(order.getStatus())) {
                throw new JeecgBootException("出库单" + order.getOrderNo() + "不是审核通过或分配失败状态, 不能分配库存");
            }
            // 出库单没有指定仓库则不允许分配库存
            if (oConvertUtils.isEmpty(order.getWarehouseId())) {
                throw new JeecgBootException("出库单" + order.getOrderNo() + "没有指定仓库, 不能分配库存");
            }
            orderList.add(order);
        }
        //2. 逐个出库单分配
        int successCount = 0;
        List<String> failedOrderNos = new ArrayList<>();
        for (WmsOutOrders order : orderList) {
            if (allocateOrder(order)) {
                successCount++;
            } else {
                failedOrderNos.add(order.getOrderNo());
            }
        }
        if (failedOrderNos.isEmpty()) {
            return "库存分配成功, 共" + successCount + "张出库单";
        }
        return "库存分配完成: 成功" + successCount + "张, 库存不足分配失败" + failedOrderNos.size() + "张(" + String.join("、", failedOrderNos) + ")";
    }

    /**
     * 给一张出库单分配库存
     *
     * @return true: 所有明细都分配成功
     */
    private boolean allocateOrder(WmsOutOrders order) {
        List<WmsOutOrdersItems> itemsList = wmsOutOrdersItemsMapper.selectByMainId(order.getId());
        if (itemsList == null || itemsList.isEmpty()) {
            throw new JeecgBootException("出库单" + order.getOrderNo() + "没有出库明细, 不能分配库存");
        }
        boolean allAllocated = true;
        for (WmsOutOrdersItems item : itemsList) {
            // 出库单明细状态为已创建或分配失败才需要分配; 已经分配好的明细跳过
            boolean needAllocate = WarehouseDictEnum.OUTBOUND_DETAIL_CREATED.getCode().equals(item.getStatus())
                    || WarehouseDictEnum.OUTBOUND_DETAIL_FAILED.getCode().equals(item.getStatus());
            if (!needAllocate) {
                continue;
            }
            if (!allocateItem(order, item)) {
                allAllocated = false;
            }
        }
        // 更新出库单状态: 明细全部分配成功为已分配, 否则为分配失败
        changeStatus(order, allAllocated
                ? WarehouseDictEnum.OUTBOUND_ALLOCATED.getCode()
                : WarehouseDictEnum.OUTBOUND_FAILED.getCode());
        return allAllocated;
    }

    /**
     * 给一条出库单明细分配库存: 要么全部分配成功, 要么一点都不分配(库存不够时不锁定任何库存)
     *
     * @return true: 分配成功
     */
    private boolean allocateItem(WmsOutOrders order, WmsOutOrdersItems item) {
        int needQuantity = item.getExpectedQuantity() == null ? 0 : item.getExpectedQuantity();
        WmsOutOrdersItems itemUpdate = new WmsOutOrdersItems();
        itemUpdate.setId(item.getId());

        //1. 找到可以分配的库存: 可售、可用数量大于0; 明细指定了批号就只找该批号
        //   返回的顺序就是分配顺序: 先到期先出, 到期日相同的先进先出
        List<WmsInventory> inventoryList = wmsInventoryService.listAllocatable(item.getSkuId(), order.getWarehouseId(), item.getBatchNumber());
        int totalAvailable = 0;
        for (WmsInventory inventory : inventoryList) {
            totalAvailable += inventory.getAvailableQuantity() == null ? 0 : inventory.getAvailableQuantity();
        }
        //2. 可用库存总数不够: 明细标记为分配失败, 不锁定库存
        if (needQuantity <= 0 || totalAvailable < needQuantity) {
            itemUpdate.setStatus(WarehouseDictEnum.OUTBOUND_DETAIL_FAILED.getCode());
            itemUpdate.setAllocatedQuantity(0);
            wmsOutOrdersItemsMapper.updateById(itemUpdate);
            return false;
        }
        //3. 按顺序从每条库存里取, 直到取够
        int remaining = needQuantity;
        for (WmsInventory inventory : inventoryList) {
            if (remaining <= 0) {
                break;
            }
            int available = inventory.getAvailableQuantity() == null ? 0 : inventory.getAvailableQuantity();
            int take = Math.min(remaining, available);
            if (take <= 0) {
                continue;
            }
            //3.1 锁定库存: 可用数量 - take, 分配数量 + take
            //    锁定失败说明这期间库存被别人占用了, 抛异常让整个操作回滚, 用户重试即可
            if (!wmsInventoryService.lockStock(inventory.getId(), take)) {
                throw new JeecgBootException("商品" + item.getProductName() + "的库存发生了变化, 请重新分配");
            }
            //3.2 向库存分配表插入一条记录: 记下从哪个储位、哪条库存分配了多少
            WmsOutOrdersAllocation allocation = new WmsOutOrdersAllocation();
            allocation.setOrderId(order.getId());
            allocation.setOrderItemId(item.getId());
            allocation.setSkuId(item.getSkuId());
            allocation.setLocationCode(inventory.getLocationCode());
            allocation.setBatchNumber(inventory.getBatchNumber());
            allocation.setContainerCode(inventory.getContainerCode());
            allocation.setAllocatedQuantity(take);
            allocation.setPickedQuantity(0);
            allocation.setInventoryId(inventory.getId());
            allocation.setStatus(WarehouseDictEnum.OUTBOUND_DETAIL_ALLOCATED.getCode());
            wmsOutOrdersAllocationMapper.insert(allocation);
            remaining -= take;
        }
        //4. 更新出库单明细: 分配数量、状态为已分配
        itemUpdate.setAllocatedQuantity(needQuantity);
        itemUpdate.setStatus(WarehouseDictEnum.OUTBOUND_DETAIL_ALLOCATED.getCode());
        wmsOutOrdersItemsMapper.updateById(itemUpdate);
        return true;
    }

    /**
     * 把逗号分割的 id 字符串拆开: 去空格、去空串、去重
     */
    private List<String> splitIds(String ids) {
        Set<String> idSet = new LinkedHashSet<>();
        if (oConvertUtils.isNotEmpty(ids)) {
            for (String raw : ids.split(",")) {
                String id = raw.trim();
                if (oConvertUtils.isNotEmpty(id)) {
                    idSet.add(id);
                }
            }
        }
        return new ArrayList<>(idSet);
    }
}
