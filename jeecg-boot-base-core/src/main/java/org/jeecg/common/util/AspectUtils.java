package org.jeecg.common.util;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;

import java.lang.reflect.Method;

/**
 * 切面工具(第三章 9.7.4, 课件说从课程资料拷贝, 这里是自己写的)
 */
public class AspectUtils {

    private static final ParameterNameDiscoverer NAME_DISCOVERER = new DefaultParameterNameDiscoverer();

    private AspectUtils() {
    }

    /**
     * 获取被拦截的方法
     */
    public static Method getMethod(ProceedingJoinPoint pjp) {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method = signature.getMethod();
        // 方法声明在接口上时, 换成实现类里的方法, 才能拿到参数名和实现类上的注解
        if (method.getDeclaringClass().isInterface() && pjp.getTarget() != null) {
            try {
                method = pjp.getTarget().getClass().getMethod(method.getName(), method.getParameterTypes());
            } catch (NoSuchMethodException ignored) {
                // 找不到就用原来的方法
            }
        }
        return method;
    }

    /**
     * 解析 formatter 里的 #{参数名}
     *
     * @param formatter 例如 ORDERS:CREATEWAYBILL:LOCK:#{orderId}
     * @param method    被拦截的方法
     * @param args      方法参数值
     */
    public static String parse(String formatter, Method method, Object[] args) {
        String[] paramNames = NAME_DISCOVERER.getParameterNames(method);
        return SpelUtils.parse(formatter, paramNames, args);
    }
}
