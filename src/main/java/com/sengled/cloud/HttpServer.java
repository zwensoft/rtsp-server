package com.sengled.cloud;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.webjars.WebJarExtractor;

import com.codahale.metrics.Clock.CpuTimeClock;
import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.jetty8.InstrumentedSocketConnector;

public class HttpServer {
    public static void main(String[] args) throws Exception {
        Entry<String, String> entry = org.webjars.WebJarAssetLocator.getWebJar("/META-INF/resources/webjars/jquery/3.0.0-beta1/dist/jquery.min.js");
        System.out.println(entry);
        
        WebJarExtractor extractor = new WebJarExtractor();
        new File("asset").mkdirs();
        extractor.extractAllWebJarsTo(new File("asset"));

        // The Server
        Server server = new Server();
        // HTTP connector

        MetricRegistry registry = new MetricRegistry();
        InstrumentedSocketConnector connector = new InstrumentedSocketConnector(registry, 10080, new CpuTimeClock());
        ConsoleReporter.forRegistry(registry).build().start(5, TimeUnit.SECONDS);
        
        // Set the connector
        server.addConnector(connector);
        
        ResourceHandler resourceHandler = new ResourceHandler();
        resourceHandler.setDirectoriesListed(false);
        
        ContextHandlerCollection handlers = new ContextHandlerCollection();

        ContextHandler onHandler = new ContextHandler("/index.html");
        onHandler.setHandler(new HelloHandler("Root Hello"));
        handlers.addHandler(onHandler);

        // Set a handler
        server.setHandler(handlers);

        // Start the server
        server.start();
        server.join();
    }


    public static class HelloHandler extends AbstractHandler
    {
        final String greeting;
        final String body;

        public HelloHandler()
        {
            this("Hello World");
        }

        public HelloHandler(String greeting)
        {
            this(greeting, null);
        }

        public HelloHandler(String greeting, String body)
        {
            this.greeting = greeting;
            this.body = body;
        }

        public void handle(String target,
                           Request baseRequest,
                           HttpServletRequest request,
                           HttpServletResponse response) throws IOException,
                ServletException
        {
            response.setContentType("text/html; charset=utf-8");
            response.setStatus(HttpServletResponse.SC_OK);

            PrintWriter out = response.getWriter();

            out.println("<h1>" + greeting + "</h1>");
            if (body != null)
            {
                out.println(body);
            }

            baseRequest.setHandled(true);
        }
    }
}
