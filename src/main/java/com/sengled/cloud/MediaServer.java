package com.sengled.cloud;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.dom4j.DocumentException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sengled.cloud.mediaserver.RtspClients;
import com.sengled.cloud.mediaserver.RtspServerBootstrap;
import com.sengled.cloud.mediaserver.rtsp.ServerEngine;
import com.sengled.cloud.mediaserver.spring.reports.RtspSessionLogger;
import com.sengled.cloud.mediaserver.spring.reports.SpringStarter;
import com.sengled.cloud.mediaserver.xml.MediaServerConfigs;
import com.sengled.cloud.mediaserver.xml.StreamSourceDef;

/**
 * rtsp media server
 * 
 * @author 陈修恒
 */
public class MediaServer {
    private static final String PORT_TALKBACK_SERVER = "talkback-server";
    private static final String PORT_RTSP_SERVER = "rtsp-server";

    private static final Logger logger = LoggerFactory.getLogger(MediaServer.class);
    
    
    public static void main(String[] args) throws InterruptedException, IOException, DocumentException {
        File configFile = getConfigFile(args);
        if (null == configFile) {
            System.exit(-1);
        }
        
        MediaServerConfigs configs;
        InputStream in = null;
        try {
            logger.info("load {}", configFile.getAbsolutePath());
            in = new FileInputStream(configFile);
            configs = MediaServerConfigs.load(in);
        } finally {
            IOUtils.closeQuietly(in);
        }


        List<RtspServerBootstrap> bootstraps = new ArrayList<RtspServerBootstrap>();
        
                
        // 构造 rtsp-server
        ServerEngine rtspServerEngine = new ServerEngine();
        Integer rtspServerPort = configs.getPorts().get(PORT_RTSP_SERVER);
        if (null != rtspServerPort) {
            bootstraps.add(new RtspServerBootstrap("rtsp-server", rtspServerEngine, rtspServerPort));

            for (StreamSourceDef def : configs.getStreamSources()) {
                try {
                    RtspClients.open(rtspServerEngine, def.getUrl(), def.getName());
                } catch (ConnectException ex) {
                   logger.warn("can't open stream[{}] url='{}'", def.getName(), def.getUrl());
                   logger.debug("{}", ex.getMessage(), ex);
                }
            }
        }

        // 构造 talkback-server
        ServerEngine talkbackEngine = new ServerEngine();
        Integer talkbackServerPort = configs.getPorts().get(PORT_TALKBACK_SERVER);
        if (null != talkbackServerPort) {
            bootstraps.add(new RtspServerBootstrap("talkback-server", talkbackEngine, talkbackServerPort));
        }

        // 启动 spring 容器
        if (!"local".equalsIgnoreCase(configs.getMode())) {
            SpringStarter starter = new SpringStarter(configFile);
            starter.start();
            
            if (null != rtspServerPort) {
                starter.setMediaResource(rtspServerPort, rtspServerEngine);
            }
            
            if (null != talkbackServerPort) {
                starter.setTalkbackResource(talkbackServerPort, talkbackEngine);
            }
            
        } else {        
            logger.warn("use local mode, don't start spring");
            RtspSessionLogger sessionLogger = new RtspSessionLogger();
            if (null != rtspServerPort) {
                sessionLogger.register(rtspServerPort, rtspServerEngine);
            }
            
            if (null != talkbackServerPort) {
                sessionLogger.register(talkbackServerPort, talkbackEngine);
            }
        }

        
        // 启动媒体服务器
        for (RtspServerBootstrap rtspServerBootstrap : bootstraps) {
            rtspServerBootstrap.start();
        }
        
    }

    private static File getConfigFile(String[] args) {
        File configFile = null;
        if (args.length > 0) { // read config from args
            configFile = new File(FilenameUtils.normalize(args[0])).getAbsoluteFile();
            if(configFile.exists()) {
                return configFile;
            } else {
                throw new IllegalArgumentException(configFile.getAbsolutePath() + " NOT Existed.");
            }
        }
        
        return new File(MediaServer.class.getResource("/config/server.xml").getFile());
    }
}
