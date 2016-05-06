package com.sengled.cloud.mediaserver.spring.reports;

import java.io.File;
import java.net.UnknownHostException;

import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import com.sengled.cloud.mediaserver.rtsp.ServerEngine;
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

    public void initMediaResource(Integer rtspServerPort,
                                  ServerEngine rtspServerCtx) throws UnknownHostException {
        if (null != springContext) {
            MediaResource mediaResource = springContext.getBean(MediaResource.class);
            mediaResource.start(rtspServerPort, rtspServerCtx);
            
            RtspSessionLogger sessionLogger = springContext.getBean(RtspSessionLogger.class);
            sessionLogger.start(rtspServerPort, rtspServerCtx);
        }
    }

    public void initTalkbackResource(Integer talkbackServerPort,
                                     ServerEngine talkbackServerCtx) throws UnknownHostException {
        if (null != springContext) {
            TalkbackResource resource = springContext.getBean(TalkbackResource.class);
            resource.start(talkbackServerPort, talkbackServerCtx);
            

            RtspSessionLogger sessionLogger = springContext.getBean(RtspSessionLogger.class);
            sessionLogger.start(talkbackServerPort, talkbackServerCtx);
        }
    }

}
