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
import com.sengled.cloud.mediaserver.RtspServer;
import com.sengled.cloud.mediaserver.spring.reports.SpringStarter;
import com.sengled.cloud.mediaserver.xml.MediaServerConfigs;
import com.sengled.cloud.mediaserver.xml.StreamSourceDef;

/**
 * rtsp media server
 * 
 * @author 陈修恒
 */
public class MediaServer {
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
    	int[] ports = configs.getPorts();
        for (int i = 0; i < ports.length; i++) {
            RtspServer.getInstance().setPort(ports[i]);
        }

        
        // 启动 rtsp server
        RtspServer.getInstance().start();

        // 启动 spring 容器
        new SpringStarter(configDir).start();
        
        
        for (StreamSourceDef def : configs.getStreamSources()) {
        	try {
                RtspClients.open(def.getUrl(), def.getName());
            } catch (ConnectException ex) {
               logger.warn("can't open stream[{}] url='{}'", def.getName(), def.getUrl());
               logger.debug("{}", ex.getMessage(), ex);
            }
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
