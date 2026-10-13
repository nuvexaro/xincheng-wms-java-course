package org.jeecg.modules.wms.wave.strategy;

import cn.hutool.core.collection.CollUtil;
import org.jeecg.modules.wms.outorder.entity.WmsOutOrders;
import org.jeecg.modules.wms.outorder.entity.WmsOutOrdersAllocation;
import org.jeecg.modules.wms.outorder.service.IWmsOutOrdersItemsService;
import org.jeecg.modules.wms.wave.entity.WmsWaveStrategy;
import org.jeecg.modules.wms.wave.service.IWaveStrategyService;
import org.jeecg.modules.wms.wave.service.IWmsWaveMasterService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 一品N件波次策略 SIFQ (Single Item Fixed Quantity): 一品2件 编码 SIFQ_2, 一品3件 编码 SIFQ_3
 * 条件: 出库单只有一种商品, 且数量正好为 N 件; 相同商品的出库单合成一个波次
 * 注意: 这个类没有 @Component, 因为 N 不同就是不同的策略, 在创建波次时 new 出来(课件 4.6)
 */
public class SIFQWaveStrategy extends IWaveStrategy{

    private final IWmsWaveMasterService wmsWaveMasterService;
    private final IWaveStrategyService strategyService;
    private final IWmsOutOrdersItemsService wmsOutOrdersItemsService;
    /** 一品几件 */
    private final int quantity;

    /**
     * 构造方法(作用和课件里的 @RequiredArgsConstructor 一样: 由 spring 把需要的 service 传进来)
     */
    public SIFQWaveStrategy(IWmsWaveMasterService wmsWaveMasterService, IWaveStrategyService strategyService,
                            IWmsOutOrdersItemsService wmsOutOrdersItemsService, int quantity) {
        this.wmsWaveMasterService = wmsWaveMasterService;
        this.strategyService = strategyService;
        // 课件的构造方法带这个参数, 这里保留, 方便以后需要按出库明细判断时使用
        this.wmsOutOrdersItemsService = wmsOutOrdersItemsService;
        this.quantity = quantity;
    }

    /**
     * 创建波次
     * @param orders 待处理的订单列表
     * @param allocationsMap 订单分配明细映射 key=出库单ID，value=分配明细
     */
    @Override
    void process(List<WmsOutOrders> orders, Map<String, List<WmsOutOrdersAllocation>> allocationsMap) {
        // 包裹策略: 除单品单件外, 其它波次都用按重量拆分包裹策略(课件第 8 节), 打包时要用到
        String shipmentStrategy = "SPLIT_BY_WEIGHT_STRATEGY";
        if(CollUtil.isNotEmpty(orders)){
            // 找出一品N件: 一个出库单中只有一个商品，且数量正好为N
            // (还要求这个出库单有分配明细, 下面要从分配明细里取商品id)
            List<WmsOutOrders> orderList = orders.stream()
                    .filter(order -> intValue(order.getTotalSku()) == 1 && intValue(order.getTotalQuantity()) == quantity
                            && CollUtil.isNotEmpty(allocationsMap.get(order.getId())))
                    .toList();
            if(CollUtil.isNotEmpty(orderList)){
                // 按商品来创建波次, 把相同商品的订单合并成一个波次
                // 给每个出库单设置对应的商品id: 只有一种商品, 分配明细里取第一条的商品id即可
                orderList.forEach(o -> o.setSkuId(allocationsMap.get(o.getId()).get(0).getSkuId()));

                // 最小出库单数量: 同一个商品的出库单要达到这个数量才创建波次
                int minOrderCount = getMinOrderCount();

                // 出库单按商品进行分组得到 key=商品id, value=订单列表
                Map<String, List<WmsOutOrders>> skuMap = orderList.stream().collect(Collectors.groupingBy(WmsOutOrders::getSkuId));
                // 有多少个key就创建多少个波次
                // 定义一个集合，来存储创建了波次的出库单
                List<WmsOutOrders> createdWaveOrders = new ArrayList<>();
                skuMap.forEach((skuId, ordersList) -> {
                    if(ordersList.size() >= minOrderCount) {
                        //创建波次
                        wmsWaveMasterService.addWave(ordersList, getStrategyType(), shipmentStrategy);
                        createdWaveOrders.addAll(ordersList);
                    }
                });

                // 处理完后，要移除已经处理的订单
                orders.removeAll(createdWaveOrders);
            }
            // 交给下一个处理类处理
            processNext(orders, allocationsMap);
        }
    }

    /**
     * 从波次策略表读取最小出库单数量; 表里没有这个策略或者没填时按 1 处理
     */
    private int getMinOrderCount() {
        WmsWaveStrategy strategy = strategyService.getStrategyByCode(getStrategyType());
        if (strategy == null || strategy.getMinOrderCount() == null || strategy.getMinOrderCount() < 1) {
            return 1;
        }
        return strategy.getMinOrderCount();
    }

    @Override
    String getStrategyType() {
        return "SIFQ_" + quantity;
    }

    @Override
    int getPriority() {
        return 1 + quantity;
    }
}
