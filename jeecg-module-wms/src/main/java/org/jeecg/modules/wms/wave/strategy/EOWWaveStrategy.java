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

/**
 * 尾单策略 EOW (End Of Wave), 课件 4.1
 * 把前面的策略都没有处理的出库单, 合成一个波次
 * 它会处理掉剩下的所有出库单, 所以优先级必须最低, 放在链的最后
 */
@Component
public class EOWWaveStrategy extends IWaveStrategy {

    private final IWmsWaveMasterService wmsWaveMasterService;
    private final IWaveStrategyService strategyService;

    public EOWWaveStrategy(IWmsWaveMasterService wmsWaveMasterService, IWaveStrategyService strategyService) {
        this.wmsWaveMasterService = wmsWaveMasterService;
        this.strategyService = strategyService;
    }

    @Override
    void process(List<WmsOutOrders> orders, Map<String, List<WmsOutOrdersAllocation>> allocationsMap) {
        // 包裹策略: 除单品单件外, 其它波次都用按重量拆分包裹策略(课件第 8 节)
        String shipmentStrategy = "SPLIT_BY_WEIGHT_STRATEGY";
        if (CollUtil.isNotEmpty(orders)) {
            // 最小出库单数量: 剩下的出库单达到这个数量才创建波次
            int minOrderCount = 1;
            WmsWaveStrategy strategy = strategyService.getStrategyByCode(getStrategyType());
            if (strategy != null && strategy.getMinOrderCount() != null && strategy.getMinOrderCount() > 1) {
                minOrderCount = strategy.getMinOrderCount();
            }
            if (orders.size() >= minOrderCount) {
                // 剩下的出库单全部合成一个波次
                List<WmsOutOrders> waveOrders = new ArrayList<>(orders);
                wmsWaveMasterService.addWave(waveOrders, getStrategyType(), shipmentStrategy);
                // 处理完后，要移除已经处理的订单
                orders.removeAll(waveOrders);
            }
            // 交给下一个处理类处理(尾单在最后, 一般没有下一个了)
            processNext(orders, allocationsMap);
        }
    }

    @Override
    String getStrategyType() {
        return "EOW";
    }

    @Override
    int getPriority() {
        // 最低优先级: 必须最后执行
        return 100;
    }
}
