package org.jeecg.modules.wms.wave.strategy;

import org.jeecg.modules.wms.outorder.entity.WmsOutOrders;
import org.jeecg.modules.wms.outorder.entity.WmsOutOrdersAllocation;

import java.util.List;
import java.util.Map;

/**
 * 波次策略(课件 4.4)
 * 责任链模式: 每个策略处理自己能处理的出库单, 处理完把剩下的交给下一个策略
 */
public abstract class IWaveStrategy {

    /**
     * 下一个策略
     */
    private IWaveStrategy nextStrategy;

    public void setNextStrategy(IWaveStrategy nextStrategy) {
        this.nextStrategy = nextStrategy;
    }

    /**
     * 创建波次
     * @param orders 待处理的订单列表(处理过的订单要从这个列表里移除)
     * @param allocationsMap 订单分配明细映射 key=出库单ID, value=分配明细
     */
    abstract void process(List<WmsOutOrders> orders,
                          Map<String, List<WmsOutOrdersAllocation>> allocationsMap);

    /**
     * 获取策略类型(和波次策略表的 strategy_code 一致)
     */
    abstract String getStrategyType();

    /**
     * 获取策略优先级(数值越小优先级越高)
     */
    abstract int getPriority();

    /**
     * 下一个策略
     */
    public IWaveStrategy next(){
        return nextStrategy;
    }

    /**
     * 执行下一个策略: 还有没处理的订单, 并且有下一个策略时才往下传
     */
    public void processNext(List<WmsOutOrders> orders, Map<String, List<WmsOutOrdersAllocation>> allocationsMap) {
        if (orders.size() > 0 && next() != null) {
            next().process(orders, allocationsMap);
        }
    }

    /**
     * 取出整数, null 当作 0 (出库单的总数量、总sku数如果没维护, 避免空指针)
     */
    static int intValue(Integer value) {
        return value == null ? 0 : value;
    }
}
