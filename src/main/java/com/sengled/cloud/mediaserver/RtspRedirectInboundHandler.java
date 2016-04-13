package com.sengled.cloud.mediaserver;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.ReferenceCountUtil;

import java.util.Map.Entry;

import org.slf4j.LoggerFactory;

public class RtspRedirectInboundHandler extends ChannelInboundHandlerAdapter {
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(RtspRedirectInboundHandler.class);
    
    
    private static String newLocation;
    private DefaultFullHttpRequest request = null;
    
    public static void setNewLocation(String newLocation) {
        RtspRedirectInboundHandler.newLocation = newLocation;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);

        logger.info("channel open {} with local {}", ctx.channel().remoteAddress(), ctx.channel().localAddress());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        ReferenceCountUtil.release(request);
        request = null;
        
        super.channelInactive(ctx);

        logger.info("channel close {} with local {}", ctx.channel().remoteAddress(), ctx.channel().localAddress());
    }
    
    @Override
    public void channelRead(ChannelHandlerContext ctx,
                            Object msg) throws Exception {
        if (msg instanceof HttpRequest) {
            HttpRequest request = (HttpRequest) msg;
            HttpMethod method = request.getMethod();
            HttpHeaders headers = request.headers();
            if (logger.isDebugEnabled()) {
                logger.debug("{} {}", method, request.getUri());
                for (Entry<String, String> entry : headers) {
                    logger.debug("{}={}", entry.getKey(), entry.getValue());
                }
            }

            ReferenceCountUtil.release(this.request);
            this.request =
                    new DefaultFullHttpRequest(
                            request.getProtocolVersion(), method, request.getUri());
            this.request.headers().set(request.headers());
        }

        if (msg instanceof HttpContent) {
            HttpContent content = (HttpContent) msg;
            this.request.content().writeBytes(content.content());
        }

        if (msg instanceof LastHttpContent) {
            String url = request.getUri();
            String newUrl = null;
            if (url.startsWith("rtsp://")) {
                int beginIndex = url.indexOf("/", "rtsp://".length());
                newUrl = newLocation + "/proxy" + url.substring(beginIndex);
            } else {
                throw new IllegalArgumentException(url + " is NOT rtsp url, such as rtsp://ip[:host]/uri");
            }
            
            FullHttpResponse response = RtspServerInboundHandler.makeResponseWithStatus(request, HttpResponseStatus.MOVED_PERMANENTLY);
            response.headers().add(HttpHeaders.Names.LOCATION, newUrl);
            
            logger.info("redirect to {} from {}", newUrl, url);
            ctx.writeAndFlush(response);
        }
    }
}
