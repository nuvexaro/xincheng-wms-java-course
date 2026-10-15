package org.jeecg.common.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.jeecg.common.aspect.annotation.Lock;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.util.AspectUtils;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * 分布式锁切面(第三章 9.7.4)
 * 方法上加了 @Lock 注解, 调用时先获取 redisson 分布式锁, 拿到锁才执行方法, 执行完释放锁
 */
@Aspect
@Component
public class LockAspect {

    private final RedissonClient redissonClient;

    public LockAspect(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    @Around("@annotation(lock)")
    public Object handleLock(ProceedingJoinPoint pjp, Lock lock) throws Throwable {
        // 锁key的格式化字符，此字符串中有spEL表达式
        String formatter = lock.formatter();
        Method method = AspectUtils.getMethod(pjp);
        Object[] args = pjp.getArgs();
        // 得到锁的key
        String redisKey = AspectUtils.parse(formatter, method, args);
        // 获取锁阻塞等待的时间,如果是0表示去尝试获取锁，如果获取不到则结束
        long waitTime = 0;
        // 阻塞等待获取锁
        if (lock.block()) {
            waitTime = lock.waitTime();
        }
        // 加锁时长
        long time = lock.time();
        // 启动看门狗自动续期: 加锁时长传 -1
        if (lock.startDog()) {
            time = -1;
            // 如果设置自动续期必须在方法执行后释放锁, 否则看门狗会一直续期, 锁永远不释放
            if (!lock.unlock()) {
                throw new JeecgBootException("@Lock 注解配置错误: startDog=true 时 unlock 必须为 true");
            }
        }
        // 得到锁对象
        RLock rLock = redissonClient.getLock(redisKey);
        // 尝试加锁
        boolean success = rLock.tryLock(waitTime, time, lock.unit());
        if (!success && !lock.block()) {
            // 未阻塞要求的情况下未得到锁
            throw new JeecgBootException("操作频繁,请稍后重试");
        }
        if (!success) {
            // 阻塞情况下未得到锁，请求超时
            throw new JeecgBootException("请求超时");
        }
        try {
            return pjp.proceed();
        } finally {
            // 只释放当前线程自己持有的锁(锁已经过期被别人拿走时不能去删别人的锁)
            if (lock.unlock() && rLock.isHeldByCurrentThread()) {
                rLock.unlock();
            }
        }
    }
}
