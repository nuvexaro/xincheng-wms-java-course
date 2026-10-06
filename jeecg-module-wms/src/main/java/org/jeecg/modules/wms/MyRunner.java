package org.jeecg.modules.wms;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class MyRunner implements CommandLineRunner {

    @Override
    public void run(String... args) throws Exception {
        System.err.println("main方法中的SpringApplication.run执行后执行");
    }
}