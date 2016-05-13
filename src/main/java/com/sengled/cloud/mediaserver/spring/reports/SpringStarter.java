package com.sengled.cloud.mediaserver.spring.reports;

import java.io.File;
import java.net.UnknownHostException;

import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import com.codahale.metrics.MetricRegistry;
import com.sengled.cloud.mediaserver.rtsp.ServerEngine;
import com.sengled.cloud.mediaserver.spring.monitor.OSMonitor;
import com.sengled.cloud.mediaserver.spring.reports.redis.MediaResource;
import com.sengled.cloud.mediaserver.spring.reports.redis.TalkbackResource;

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

    public void setMediaResource(Integer rtspServerPort,
                                  ServerEngine rtspServerCtx) throws UnknownHostException {
        if (null != springContext) {
            MediaResource mediaResource = springContext.getBean(MediaResource.class);
            mediaResource.register(rtspServerPort, rtspServerCtx);
            
            RtspSessionLogger sessionLogger = springContext.getBean(RtspSessionLogger.class);
            sessionLogger.register(rtspServerPort, rtspServerCtx);
        }
    }

    public void setTalkbackResource(Integer talkbackServerPort,
                                     ServerEngine talkbackServerCtx) throws UnknownHostException {
        if (null != springContext) {
            TalkbackResource resource = springContext.getBean(TalkbackResource.class);
            resource.register(talkbackServerPort, talkbackServerCtx);
            

            RtspSessionLogger sessionLogger = springContext.getBean(RtspSessionLogger.class);
            sessionLogger.register(talkbackServerPort, talkbackServerCtx);
        }
    }

    public void withMetricRegistry(MetricRegistry metrics) {
        OSMonitor osMonitor =  springContext.getBean(OSMonitor.class);
        osMonitor.withMetricRegistry(metrics);
    }

}
