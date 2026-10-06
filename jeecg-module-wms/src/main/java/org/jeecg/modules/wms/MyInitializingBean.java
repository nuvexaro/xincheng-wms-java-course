package org.jeecg.modules.wms;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

// 缓存预热
// 【推荐】 InitializingBean
@Component
// spring ioc容器帮我们创建对象后，就会调用 InitializingBean 的 afterPropertiesSet 方法
public class MyInitializingBean implements InitializingBean {

    @Override
    public void afterPropertiesSet() throws Exception {
        // 给对象的属性赋值后执行
        System.err.println("查询数据库的数据存入redis中");
    }
}