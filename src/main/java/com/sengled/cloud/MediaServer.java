package com.sengled.cloud;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.PropertyConfigurator;
import org.dom4j.DocumentException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sengled.cloud.mediaserver.RtspClients;
import com.sengled.cloud.mediaserver.RtspServerBootstrap;
import com.sengled.cloud.mediaserver.rtsp.ServerEngine;
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
        File configDir = getConfigDir(args);
        if (null == configDir) {
            System.exit(-1);
        }
        
        MediaServerConfigs configs;
        InputStream in = null;
        try {
            in = new FileInputStream(new File(configDir, "server.xml"));
            configs = MediaServerConfigs.load(in);
        } finally {
            IOUtils.closeQuietly(in);
        }


        
        // 启动 rtsp-server
        ServerEngine rtspServerEngine = new ServerEngine();
        Integer rtspServerPort = configs.getPorts().get(PORT_RTSP_SERVER);
        if (null != rtspServerPort) {
            new RtspServerBootstrap("rtsp-server", rtspServerEngine, rtspServerPort).start();

            for (StreamSourceDef def : configs.getStreamSources()) {
                try {
                    RtspClients.open(rtspServerEngine, def.getUrl(), def.getName());
                } catch (ConnectException ex) {
                   logger.warn("can't open stream[{}] url='{}'", def.getName(), def.getUrl());
                   logger.debug("{}", ex.getMessage(), ex);
                }
            }
        }

        // 启动 talkback-server
        ServerEngine talkbackEngine = new ServerEngine();
        Integer talkbackServerPort = configs.getPorts().get(PORT_TALKBACK_SERVER);
        if (null != talkbackServerPort) {
            new RtspServerBootstrap("talkback-server", talkbackEngine, talkbackServerPort).start();
        }

        // 启动 spring 容器
        if (!"local".equalsIgnoreCase(configs.getMode())) {
            SpringStarter starter = new SpringStarter(configDir);
            starter.start();
            
            if (null != rtspServerPort) {
                starter.initMediaResource(rtspServerPort, rtspServerEngine);
            }
            
            if (null != talkbackServerPort) {
                starter.initTalkbackResource(talkbackServerPort, talkbackEngine);
            }
            
        } else {        
            logger.warn("use local mode, don't start spring");
        }

        
        
        
    }

    private static File getConfigDir(String[] args) {
        File configDir = null;
        if (args.length > 1) { // read config from args
            configDir = new File(FilenameUtils.normalize(args[0])).getAbsoluteFile();
        }
        
        if (null == configDir) { // read config from jar
            String serverConfigUrl = MediaServer.class.getResource("/config/server.xml").getFile();
            if (null != serverConfigUrl && new File(serverConfigUrl).exists()) {
                configDir = new File(serverConfigUrl).getAbsoluteFile().getParentFile();
            }
        } else {  // log4j 配置文件
            PropertyConfigurator.configure(new File(configDir , "log4j.properties").getAbsolutePath());
        }

        if (null == configDir) {
            logger.error("cant load configs");
            System.out.println("Usage: " + MediaServer.class.getCanonicalName() + " ./config");
            return null;
        } else {
            System.out.println(MediaServer.class.getCanonicalName() + " " + configDir.getAbsolutePath());
        }
        
        return configDir;
    }
}
