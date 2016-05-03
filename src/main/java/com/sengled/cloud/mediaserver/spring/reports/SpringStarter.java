package com.sengled.cloud.mediaserver.spring.reports;

import java.io.File;

import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * 启动 spring
 * 
 * @author 陈修恒
 * @date 2016年5月3日
 */
public class SpringStarter {
    
    private ApplicationContext springContext ;
    
    public SpringStarter(File configDir) {
    }
    
    public void start() {
        springContext = new ClassPathXmlApplicationContext("/applicationContext.xml");
    }
    
    public ApplicationContext getSpringContext() {
        return springContext;
    }
}
