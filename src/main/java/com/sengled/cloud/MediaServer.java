package com.sengled.cloud;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.ConnectException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Slf4jReporter;
import com.sengled.cloud.http.HttpServer;
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
    private static final String PORT_HTTP_SERVER = "http-server";

    private static final Logger logger = LoggerFactory.getLogger(MediaServer.class);
    
    
    public static void main(String[] args) throws Exception {
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

        // 性能统计
        final MetricRegistry metrics = new MetricRegistry();
        Integer httpPort = configs.getPorts().get(PORT_HTTP_SERVER);
        if (null != httpPort) {
            new HttpServer(httpPort).withMetricRegistry(metrics).start();
        }

        // 默认启动的线程数
        int defaultWorkerThreads = Runtime.getRuntime().availableProcessors() * 2;
        String workerThreadsProperty = System.getProperty(SystemPropertyKeys.WORKER_THREADS, String.valueOf(defaultWorkerThreads));
        int maxWorkerThreads = Integer.valueOf(workerThreadsProperty);
        
        
        List<RtspServerBootstrap> bootstraps = new ArrayList<RtspServerBootstrap>();
                
        // 构造 rtsp-server
        ServerEngine rtspServerEngine = new ServerEngine();
        Integer rtspServerPort = configs.getPorts().get(PORT_RTSP_SERVER);
        if (null != rtspServerPort) {
            rtspServerEngine.withMetricRegistry("rtsp-server", metrics);
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
            talkbackEngine.withMetricRegistry("talkback-server", metrics);
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

        // 日志输出
        Slf4jReporter.forRegistry(metrics)
                .convertRatesTo(TimeUnit.SECONDS)
                .convertDurationsTo(TimeUnit.SECONDS)
                .outputTo(logger)
                .build()
                .start(10, TimeUnit.SECONDS);
        
        // 启动媒体服务器
        EventLoopGroup bossGroup;
        EventLoopGroup workerGroup;
        Class<? extends ServerChannel> channelClass;
        if (bootstraps.isEmpty()) {
            logger.error("NO rtsp server started");
            System.exit(-1);
            return;
        } else if(Epoll.isAvailable()) {
            bossGroup = new EpollEventLoopGroup(bootstraps.size());
            workerGroup = new EpollEventLoopGroup(maxWorkerThreads);
            channelClass = EpollServerSocketChannel.class;
        } else {
            bossGroup = new NioEventLoopGroup(bootstraps.size());
            workerGroup = new NioEventLoopGroup(maxWorkerThreads);
            channelClass = NioServerSocketChannel.class;
        }

        logger.warn("!!! ServerChannel used '{}'", channelClass);
        for (RtspServerBootstrap rtspServerBootstrap : bootstraps) {
            rtspServerBootstrap.group(bossGroup, workerGroup)
                               .channel(channelClass)
                               .start();
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
