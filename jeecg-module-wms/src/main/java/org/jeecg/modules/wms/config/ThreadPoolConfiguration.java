package org.jeecg.modules.wms.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 生成运单的线程池(课件 9.4.1 第二种方式: spring 封装的线程池)
 * @EnableScheduling: 开启 spring 定时任务(课件 9.3); 项目里原来没有开启, 不加的话 @Scheduled 不会执行
 */
@EnableScheduling
@Configuration
public class ThreadPoolConfiguration {

    @Bean
    public ThreadPoolTaskExecutor waybillThreadPool(){
        //spring 封装的线程池
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        //核心线程数
        executor.setCorePoolSize(10);
        //最大线程数: 通常是 corePoolSize 的 2~5 倍
        executor.setMaxPoolSize(20);
        //阻塞队列的长度
        executor.setQueueCapacity(2000);
        //空闲线程的存活时长, 单位为秒
        executor.setKeepAliveSeconds(60);
        //线程名称前缀, 区分系统线程还是业务线程
        executor.setThreadNamePrefix("waybill-thread-");
        //拒绝策略: 队列满了直接丢弃(没生成运单的出库单, 下一轮定时任务还会再查到, 不会漏)
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        //初始化
        executor.initialize();
        return executor;
    }
}
