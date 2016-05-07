package com.sengled.cloud.mediaserver;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.rtsp.RtspHeaders;
import io.netty.handler.codec.rtsp.RtspMethods;
import io.netty.handler.codec.rtsp.RtspVersions;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.ReferenceCountUtil;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.sql.Timestamp;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

import javax.sdp.SessionDescription;
import javax.sip.TransportNotSupportedException;

import org.slf4j.LoggerFactory;

import com.sengled.cloud.mediaserver.rtsp.FullHttpMessageUtils;
import com.sengled.cloud.mediaserver.rtsp.RtspSession;
import com.sengled.cloud.mediaserver.rtsp.RtspSession.SessionMode;
import com.sengled.cloud.mediaserver.rtsp.ServerEngine;
import com.sengled.cloud.mediaserver.rtsp.Transport;
import com.sengled.cloud.mediaserver.rtsp.interleaved.RtcpContent;
import com.sengled.cloud.mediaserver.rtsp.interleaved.RtpPkt;
import com.sengled.cloud.mediaserver.rtsp.rtp.InterLeavedRTPSession;

/**
 * 处理客户端的 rtsp 请求。
 * 
 * <p>
 * 接收 ANOUNCE 往服务器推流
 * 同时支持  DESCRIBE 方式从服务器拉流
 * </p>
 * @author 陈修恒
 * @date 2016年4月22日
 */
public class RtspServerInboundHandler extends ChannelInboundHandlerAdapter {
    private static org.slf4j.Logger logger = LoggerFactory.getLogger(RtspServerInboundHandler.class);


    final private ServerEngine engine;
    private RtspSession session = null;
    private AtomicLong numRtp = new AtomicLong();
    
    public RtspServerInboundHandler(ServerEngine engine) {
        this.engine = engine;
    }
    
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);

        logger.info("open <{}, {}>", ctx.channel().remoteAddress(), ctx.channel().localAddress());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
        if (null != session) {
            session.destroy("channel inactive");
        }

        logger.info("close <{}, {}>", ctx.channel().remoteAddress(), ctx.channel().localAddress());
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx,
                                   Object evt) throws Exception {
        super.userEventTriggered(ctx, evt);
        
        if (evt instanceof IdleStateEvent) {
            if ( null != session && session.getMode() == SessionMode.PUBLISH) {
                throw new TimeoutException("read timeout, token = '" + session.getName() + "'");
            }
        }
    }
    
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx,
                                Throwable cause) throws Exception {
        if(cause instanceof IOException) {
            logger.info("IOException {}", cause.getMessage());
        } else if (cause  instanceof TimeoutException) {
            logger.error("idle timeout, {}.", cause.getMessage());
        } else{
            logger.error("channel close for {}", cause.getMessage(), cause);
        }

        ctx.channel().close();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx,
                            Object msg) throws Exception {
        try {
            if (msg instanceof FullHttpRequest) {
                FullHttpRequest request = (FullHttpRequest) msg;
                FullHttpMessageUtils.log(logger, request).info();
                
                handleRequest(ctx, request);
            } else if (null != session) {
                // 不是 http 请求, 则可能是  rtp 数据
                handleRtps(msg);
            }
        } finally {
            ReferenceCountUtil.release(msg);
        }
    }

    private void handleRtps(Object msg) {
        if (msg instanceof RtpPkt) {
            long num = numRtp.incrementAndGet();
            if (num % 500 == 0) {
                logger.debug("receive Frame[{}] of {} ", num, session);
            }
   
            session.dispatcher().dispatch(((RtpPkt) msg).retain());
        } else if (msg instanceof RtcpContent)  {
            RtcpContent rtcpObj = (RtcpContent)msg;
            session.onRtcpEvent(rtcpObj);
        } else {
            logger.info("ignore {}", msg);
        }
    }

    private void handleRequest(final ChannelHandlerContext ctx,
                        FullHttpRequest request) throws UnsupportedEncodingException {
        FullHttpResponse response = null;
        
        response = makeHttpResponse(ctx, request);
        
        if (null == response) {
            logger.info("no response");
        } else if (!ctx.channel().isWritable()) {
            logger.warn("channel writable is False");
        } else {
            FullHttpMessageUtils.log(logger, response).info();
            ctx.writeAndFlush(response);
            

            HttpMethod method = request.getMethod();
            if (RtspMethods.RECORD.equals(method)) {
                session.record();
            } else if (RtspMethods.PLAY.equals(method)) {
                session.play();
            }
        }
    }

    private FullHttpResponse makeHttpResponse(final ChannelHandlerContext ctx,
                                              FullHttpRequest request)
            throws UnsupportedEncodingException {
        FullHttpResponse response;
        HttpMethod method = request.getMethod();
        if (RtspMethods.OPTIONS.equals(method)) {
            response = makeResponseWithStatus(request, HttpResponseStatus.OK);
            
            response.headers().add(RtspHeaders.Names.PUBLIC, "OPTIONS, DESCRIBE, PLAY, ANNOUNCE, SETUP, PLAY, GET_PARAMETER, TEARDOWN");
        }
        else if (RtspMethods.DESCRIBE.equals(method)){
            
            session = new RtspSession(engine, ctx, request.getUri());
            final RtspSession mySession = session;
            mySession.withMode(SessionMode.PLAY)
                     .withUserAgent(request.headers());
            
            response = makeResponse(request, null);
            String sdp = session.getSDP();
            if (null == sdp) {
                response.setStatus(HttpResponseStatus.NOT_FOUND);
                ctx.writeAndFlush(response);
            } else {
                logger.debug("output:\r\n{}", sdp);
                response.content().writeBytes(sdp.getBytes("UTF-8"));
                response.headers().add(RtspHeaders.Names.CACHE_CONTROL, RtspHeaders.Values.NO_CACHE);
                response.headers().add(RtspHeaders.Names.EXPIRES, response.headers().get(RtspHeaders.Names.DATE));
                response.headers().set(RtspHeaders.Names.CONTENT_LENGTH, response.content().readableBytes());
                response.headers().set(RtspHeaders.Names.CONTENT_TYPE, "application/sdp");
            }
        } 
        else if (RtspMethods.ANNOUNCE.equals(method)) {
            String sdp = request.content().toString(Charset.forName("UTF-8"));
            
            response = makeResponse(request, session);
            session = new RtspSession(engine, ctx, request.getUri())
                .withSdp(sdp)
                .withMode(SessionMode.PUBLISH)
                .withUserAgent(request.headers());
        }
        else if (null != session && RtspMethods.SETUP.equals(method)) {
            try {
                String exceptTransport = request.headers().get(RtspHeaders.Names.TRANSPORT);
                Transport transport = session.setupStream(request.getUri(), exceptTransport);

                int rtpChannel = transport.getInterleaved()[0];
                int streamIndex = session.getStreamIndex(rtpChannel);
                InterLeavedRTPSession rtpSession = session.getRTPSessions()[streamIndex];
                transport.setSsrc(rtpSession.ssrc());
                
                response = makeResponse(request, session);
                response.headers().add(RtspHeaders.Names.CACHE_CONTROL, RtspHeaders.Values.NO_CACHE);
                response.headers().add(RtspHeaders.Names.EXPIRES, response.headers().get(RtspHeaders.Names.DATE));
                response.headers().add(RtspHeaders.Names.TRANSPORT, transport.toString());
            } catch (TransportNotSupportedException ex) {
                logger.warn("Not Supported Transport '{}'", ex.getMessage(), ex);
                
                response = makeResponse(request, session);
                response.setStatus(HttpResponseStatus.NOT_EXTENDED);
            }
        }
        else if (null != session && RtspMethods.RECORD.equals(method) || RtspMethods.PLAY.equals(method)) {
            // response = makeResponse(request, null);
            response = makeResponse(request, session);
            response.headers().set(RtspHeaders.Names.RTP_INFO,  getRtpInfo(request));
        }
        else if (RtspMethods.GET_PARAMETER.equals(method)) {
            SessionDescription  sd = null;
            
            if (null != session) {
                sd = session.getSessionDescription();
            }
            if (null == sd) {
                response = makeResponse(request, session);
                response.setStatus(HttpResponseStatus.NOT_FOUND);
            } else {
                response = makeResponse(request, session);
            }
        }
        else if (RtspMethods.TEARDOWN.equals(method)) {
            if (null != session) {
                session.destroy("client teardown");
                session = null;
            }
            
            response = makeResponse(request, session);
        }
        // OTHERWISE
        else {
            logger.warn("illegal call session = {}, method = {}", session, method);
            logger.warn("{}", request.content().toString(Charset.forName("UTF-8")));

            response = makeResponse(request, session);
            response.setStatus(HttpResponseStatus.FORBIDDEN);
        }
        return response;
    }

    private String getRtpInfo(HttpRequest request) {
        StringBuilder rtpInfo = new StringBuilder();
        int i = 0;
        for (InterLeavedRTPSession rtpSession : session.getRTPSessions()) {
            if (null == rtpSession) {
                continue;
            }
            
            if (i != 0) {
                rtpInfo.append(",");
            }
            
            rtpInfo.append("url=").append(rtpSession.getMediaStream().getUrl());
            
            i++;
        }
        
        return rtpInfo.toString();
    }

    private FullHttpResponse makeResponse(HttpRequest requst,  RtspSession session) {
        FullHttpResponse resp = makeResponseWithStatus(requst, HttpResponseStatus.OK);
        if (null != session) {
            resp.headers().set(RtspHeaders.Names.SESSION, session.getId());
        }
        
        return resp;
    }
    
    
    public static FullHttpResponse makeResponseWithStatus(HttpRequest requst, HttpResponseStatus status) {
        HttpHeaders headers = requst.headers();

        FullHttpResponse resp =
                new DefaultFullHttpResponse(RtspVersions.RTSP_1_0, status);
        resp.headers().set(RtspHeaders.Names.CSEQ, headers.get(RtspHeaders.Names.CSEQ));
        resp.headers().set(RtspHeaders.Names.DATE, new Timestamp(System.currentTimeMillis()));
        resp.headers().set(RtspHeaders.Names.SERVER, "Sengled Java Media Server 1.0");

        return resp;
    }
}
