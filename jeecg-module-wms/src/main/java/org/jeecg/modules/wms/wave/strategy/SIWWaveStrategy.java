package org.jeecg.modules.wms.wave.strategy;

import cn.hutool.core.collection.CollUtil;
import org.jeecg.modules.wms.outorder.entity.WmsOutOrders;
import org.jeecg.modules.wms.outorder.entity.WmsOutOrdersAllocation;
import org.jeecg.modules.wms.wave.entity.WmsWaveStrategy;
import org.jeecg.modules.wms.wave.service.IWaveStrategyService;
import org.jeecg.modules.wms.wave.service.IWmsWaveMasterService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 一品波次策略 SIW (Single Item Wave)
 * 条件: 出库单只有一种商品, 数量不限; 相同商品的出库单合成一个波次
 */
@Component
public class SIWWaveStrategy extends IWaveStrategy{

    private final IWmsWaveMasterService wmsWaveMasterService;
    private final IWaveStrategyService strategyService;

    /**
     * 构造方法(作用和课件里的 @RequiredArgsConstructor 一样: 由 spring 把需要的 service 传进来)
     */
    public SIWWaveStrategy(IWmsWaveMasterService wmsWaveMasterService, IWaveStrategyService strategyService) {
        this.wmsWaveMasterService = wmsWaveMasterService;
        this.strategyService = strategyService;
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
            // 找出一品: 一个出库单中只有一个商品, 数量不限
            // (还要求这个出库单有分配明细, 下面要从分配明细里取商品id)
            List<WmsOutOrders> orderList = orders.stream()
                    .filter(order -> intValue(order.getTotalSku()) == 1
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
        return "SIW";
    }

    @Override
    int getPriority() {
        return 40;
    }
}
