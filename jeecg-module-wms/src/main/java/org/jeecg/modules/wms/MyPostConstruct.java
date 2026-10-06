package org.jeecg.modules.wms;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

@Component
public class MyPostConstruct {

    /**
     * PostConstruct 这个类的构造器执行后来执行这个方法
     */
    @PostConstruct
    public void abc() {
        System.err.println("MyPostConstruct.abc");
    }
}