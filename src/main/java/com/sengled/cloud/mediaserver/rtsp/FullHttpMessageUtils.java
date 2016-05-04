package com.sengled.cloud.mediaserver.rtsp;

import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;

import java.nio.charset.Charset;
import java.util.Map.Entry;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

/**
 * 
 * @author 陈修恒
 * @date 2016年5月4日
 */
public class FullHttpMessageUtils {
    private static final String LINE_SEPARATOR = "\r\n";
    private static final Charset UTF_8 = Charset.forName("UTF-8");
    
    public static MessageLogger log(Logger logger, FullHttpRequest request) {
        return new MessageLogger(logger, request);
    }
    
    public static MessageLogger log(Logger logger, FullHttpResponse response) {
        return new MessageLogger(logger, response);
    }
    
    public static final class MessageLogger {
        private Logger logger;
        private String line;
        private Object[] firstLine;
        private HttpHeaders headers;
        private String content;
        
        
        public MessageLogger(Logger logger, FullHttpRequest request) {
            this.logger = logger;
            this.line = "------------------ request ------------------";
            this.firstLine = new Object[]{request.getMethod(), request.getUri(), request.getProtocolVersion()};
            this.headers = request.headers();
            this.content = request.content().toString(UTF_8);
        }


        public MessageLogger(Logger logger, FullHttpResponse response) {
            this.logger = logger;
            this.line = "------------------ response ------------------";
            this.firstLine = new Object[]{response.getProtocolVersion(), response.getStatus().code(), response.getStatus().reasonPhrase()};
            this.headers = response.headers();
            this.content = response.content().toString(UTF_8);
        }


        public void info() {
            logger.info(line);
            logger.info("{} {} {}", firstLine);
            for (Entry<String, String> entry : headers) {
                logger.info("{}:{}", entry.getKey(), entry.getValue());
            }
            logger.info("");
            logger.info("{}", StringUtils.isEmpty(content) ? "[no-content]":content);
            logger.info("<<<");
        }
        
        @Override
        public String toString() {
            StringBuffer buf = new StringBuffer();
            for (int i = 0; i < firstLine.length; i++) {
                if (0 != i) {
                    buf.append(" ");
                }
                buf.append(firstLine[i]);
            }
            buf.append(LINE_SEPARATOR);
            for (Entry<String, String> entry : headers) {
                buf.append(entry.getKey()).append(":").append(entry.getValue()).append(LINE_SEPARATOR);
            }
            buf.append(LINE_SEPARATOR);
            buf.append(StringUtils.isEmpty(content) ? "[no-content]":content);
            buf.append(LINE_SEPARATOR).append("<<<<<<<<<<.");
            return buf.toString();
        }
    }
}
