package com.sengled.cloud.http;

import java.io.File;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FilenameUtils;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.bio.SocketConnector;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.webjars.WebJarExtractor;

import com.codahale.metrics.MetricRegistry;
import com.sengled.cloud.http.metric.MetricsGraphicsHandler;
import com.sengled.cloud.http.metric.MetricsGraphicsReporter;

/**
 * Http 服务器，
 * 
 * 用于输出   Metrics 的统计数据
 * @author 陈修恒
 * @date 2016年5月12日
 */
public class HttpServer {
    private static final Logger logger = LoggerFactory.getLogger(HttpServer.class);
    
    final private int port;
    HandlerList handlers = new HandlerList();
    final private String base;
    
    public HttpServer(int port) {
        this.port = port;
        this.base = FilenameUtils.normalize(new File("./work").getAbsolutePath());
        
        // static resource(s)
        logger.warn("use '{}' as resource base", base);
        ResourceHandler resourceHandler = new ResourceHandler();
        resourceHandler.setDirectoriesListed(true);
        resourceHandler.setResourceBase(base);
        
        handlers.addHandler(resourceHandler);
    }
    
    public HttpServer withMetricRegistry(MetricRegistry registry) {
        MetricsGraphicsHandler handler = new MetricsGraphicsHandler("/metrics/");
        
        MetricsGraphicsReporter.forRegistry(registry)
                                .convertRatesTo(TimeUnit.SECONDS)
                                .convertDurationsTo(TimeUnit.SECONDS)
                                .build(handler)
                                .start(10, TimeUnit.SECONDS);
        
        handlers.addHandler(handler);
        
        return this;
    }
    
    public void start() throws Exception {
        // http server socket
        SocketConnector connector = new SocketConnector();
        connector.setPort(port);
        
        // 导出 jar 包
        WebJarExtractor extractor = new WebJarExtractor();
        File jsDir = new File(base, "asset");
        jsDir.mkdirs();
        extractor.extractAllWebJarsTo(jsDir);
        
        // Set the connector
        Server server = new Server();
        server.addConnector(connector);
        server.setHandler(handlers);
        server.start();
    }


}
