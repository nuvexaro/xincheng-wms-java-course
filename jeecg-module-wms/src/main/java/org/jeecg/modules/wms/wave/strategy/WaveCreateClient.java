package org.jeecg.modules.wms.wave.strategy;

import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.wms.outorder.entity.WmsOutOrders;
import org.jeecg.modules.wms.outorder.entity.WmsOutOrdersAllocation;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 创建波次客户端类(课件 4.6)
 * 作用: 把用户选择的波次策略按优先级排好, 串成一条责任链, 再从第一个策略开始处理
 */
public class WaveCreateClient {

    private IWaveStrategy firstStrategy;

    /**
     * 构造方法
     *
     * @param strategies         全部波次策略
     * @param selectedStrategies 选中的波次策略编码
     */
    public WaveCreateClient(List<IWaveStrategy> strategies, List<String> selectedStrategies) {
        // 波次策略按优先级来排序
        List<IWaveStrategy> list = strategies.stream()
                // 留下用户选择的策略，没选择不要
                .filter(strategy -> selectedStrategies.contains(strategy.getStrategyType()))
                // 按优先级排序
                .sorted(Comparator.comparingInt(IWaveStrategy::getPriority)).toList();
        // 设置成链, 设置它的下一个处理者
        if (list.size() > 0) {
            // 第一个处理者
            firstStrategy = list.get(0);
            for (int i = 0; i < list.size() - 1; i++) {
                // 设置它的下一个处理者
                list.get(i).setNextStrategy(list.get(i + 1));
            }
            // 最后一个处理者没有下一个
            // (课件里没有这一句: 策略对象是 spring 的单例, 会被多次创建波次共用,
            //  如果上一次它不是最后一个, 它身上还留着上一次的"下一个", 不清掉就会执行用户这次没选的策略)
            list.get(list.size() - 1).setNextStrategy(null);
        }
    }

    /**
     * 处理订单波次分配
     */
    public void process(List<WmsOutOrders> orders,
                        Map<String, List<WmsOutOrdersAllocation>> allocationsMap) {
        if (firstStrategy == null) {
            throw new JeecgBootException("没有配置波次策略");
        }
        firstStrategy.process(orders, allocationsMap);
    }
}
