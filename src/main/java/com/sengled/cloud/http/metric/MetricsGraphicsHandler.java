package com.sengled.cloud.http.metric;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.StringUtils;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MetricsGraphicsHandler extends AbstractHandler implements MetricsGraphics {
    private static final String CONTENT_TYPE = "application/json";
    private static final Logger logger = LoggerFactory.getLogger(MetricsGraphicsHandler.class);

    private String uri;
    private String allowedOrigin;
    
    private Map<String, List<Table>> tablesList = new HashMap<String, List<Table>>();
    
    public MetricsGraphicsHandler(String uri) {
        this.uri = uri;
    }

    private Table getTable(String name,
                          String type) {

        List<Table> tables = tablesList.get(type);
        if (null == tables) {
            return null;
        }
        
        for (Table table : tables) {
            if (StringUtils.equals(name, table.getName())) {
                return table;
            }
        }
        
        return null;
    }
    
    @Override
    public Table getOrCreateTable(String name,
                          String type,
                          String colTemplates) {
        
        List<Table> tables = tablesList.get(type);
        if (null == tables) {
            tables = new ArrayList<Table>();
            tablesList.put(type, tables);
        }
        
        for (Table table : tables) {
            if (StringUtils.equals(name, table.getName())) {
                return table;
            }
        }

        Table table = null;
        if (null != colTemplates) {
            table = new Table(name, colTemplates);
            tables.add(table);
        }
        return table;
    }
    
    @Override
    public void handle(String target,
                       Request baseRequest,
                       HttpServletRequest req,
                       HttpServletResponse resp) throws IOException, ServletException {
        if (baseRequest.isHandled()) {
            return;
        } else if (!StringUtils.startsWith(baseRequest.getUri().getPath(), uri)) {
            return;
        }
        baseRequest.setHandled(true);
        
        resp.setContentType(CONTENT_TYPE);
        if (allowedOrigin != null) {
            resp.setHeader("Access-Control-Allow-Origin", allowedOrigin);
        }
        resp.setHeader("Cache-Control", "must-revalidate,no-cache,no-store");
        
        
        final OutputStream output = resp.getOutputStream();
        try {
            String type = req.getRequestURI().substring(uri.length());
            String name = req.getParameter("name");
            String colName = req.getParameter("column");
            if (null == colName) {
                colName = "count";
            }
            
            logger.info("{}, name={}, colName={}", type, name, colName);
            Table table = getTable(name, StringUtils.upperCase(type));
            if (null != table) {
                resp.setStatus(HttpServletResponse.SC_OK);
                if(table.ouput(output, colName)) {
                    return;
                }
            }

            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
        } finally {
            output.close();
        }
        
    }

}
