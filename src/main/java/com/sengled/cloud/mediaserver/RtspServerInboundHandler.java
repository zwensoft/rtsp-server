package com.sengled.cloud.mediaserver;

import io.netty.buffer.ByteBuf;
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
import io.netty.util.ReferenceCountUtil;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicLong;

import javax.sip.TransportNotSupportedException;

import org.slf4j.LoggerFactory;

import com.sengled.cloud.mediaserver.event.Event;
import com.sengled.cloud.mediaserver.event.Listener;
import com.sengled.cloud.mediaserver.rtsp.RTPSetup;
import com.sengled.cloud.mediaserver.rtsp.RtspSession;
import com.sengled.cloud.mediaserver.rtsp.RtspSession.SessionMode;
import com.sengled.cloud.mediaserver.rtsp.codec.DefaultInterleavedFrame;
import com.sengled.cloud.mediaserver.rtsp.codec.InterleavedFrame;
import com.sengled.cloud.mediaserver.rtsp.rtp.RtpEvent;

class RtspServerInboundHandler extends ChannelInboundHandlerAdapter {
    private org.slf4j.Logger logger = LoggerFactory.getLogger(getClass());

    
    private RtspSession session = null;
    private List<RTPSetup> setups = new ArrayList<RTPSetup>();
    
    private AtomicLong numRtp = new AtomicLong();
    
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);

        logger.info("channel open {} with local {}", ctx.channel().remoteAddress(), ctx.channel().localAddress());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (null != session) {
            session.destroy();
        }
        
        super.channelInactive(ctx);

        logger.info("channel close {} with local {}", ctx.channel().remoteAddress(), ctx.channel().localAddress());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx,
                                Throwable cause) throws Exception {
        if(cause instanceof IOException) {
            logger.info("IOException {}", cause.getMessage());
        } else {
            logger.error("channel close for {}", cause.getMessage(), cause);
        }

        ctx.channel().close();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx,
                            Object msg) throws Exception {
        if (msg instanceof FullHttpRequest) {
            FullHttpRequest request = (FullHttpRequest) msg;
            HttpMethod method = request.getMethod();
            HttpHeaders headers = request.headers();
            logger.info("{} {} {}", method, request.getUri(), request.getProtocolVersion());
            if (logger.isDebugEnabled()) {
                for (Entry<String, String> entry : headers) {
                    logger.debug("{}:{}", entry.getKey(), entry.getValue());
                }
            }
            
            readHttpRequest(ctx, request);
        }


        if (msg instanceof InterleavedFrame) {
            long num = numRtp.incrementAndGet();
            if (num % 500 == 0) {
                logger.debug("receive Frame[{}] of {} ", num, session);
            }

            InterleavedFrame frame = (InterleavedFrame)msg;
            if (null != session && frame.getChannel() % 2 == 0) {
                session.dispatch(frame.retain());
            }
        }
        
        ReferenceCountUtil.release(msg);
    }

    private void readHttpRequest(final ChannelHandlerContext ctx,
                        FullHttpRequest request) throws UnsupportedEncodingException {
        FullHttpResponse response = null;
        
        HttpMethod method = request.getMethod();
        
        if (RtspMethods.OPTIONS.equals(method)) {
            response = makeResponseWithStatus(request, HttpResponseStatus.OK);
            
            response.headers().add(RtspHeaders.Names.PUBLIC, "OPTIONS, DESCRIBE, PLAY, ANNOUNCE, SETUP, PLAY, GET_PARAMETER, TEARDOWN");
        }
        else if (RtspMethods.DESCRIBE.equals(method)){
            session = new RtspSession(request.getUri());
            final RtspSession mySession = session;
            mySession
                .withMode(SessionMode.PLAY)
                .withListener(new Listener() {
                    @Override
                    public void on(Event event) {
                        if(event instanceof RtpEvent) {
                            RtpEvent rtp = ((RtpEvent)event);

                            int streamIndex = rtp.getStreamIndex();
                            ByteBuf content = rtp.content();
                            
                            if (mySession.isStreamSetup(streamIndex)) {
                                int channel = mySession.getStreamRTPChannel(streamIndex);
                                
                                // netty bytebuf 缓存，只在当前线程有效， 所以需要重新拷贝
                                ByteBuf payload = ctx.alloc().buffer(content.readableBytes()).writeBytes(content);
                                DefaultInterleavedFrame newMsg = new DefaultInterleavedFrame(channel, payload);
                                logger.trace("writeAndFlush {}", newMsg);

                                ctx.writeAndFlush(newMsg);
                            }
                        }
                    }
                });
            
            response = makeResponse(request, session);
            
            String sdp = session.getSDP();
            if (null == sdp) {
                response.setStatus(HttpResponseStatus.NOT_FOUND);
                ctx.writeAndFlush(response);
                return;
            } else {
                logger.debug("output:\r\n{}", sdp);
                response.content().writeBytes(sdp.getBytes("UTF-8"));
            }
            
            response.headers().add(RtspHeaders.Names.CACHE_CONTROL, RtspHeaders.Values.NO_CACHE);
            response.headers().add(RtspHeaders.Names.EXPIRES, response.headers().get(RtspHeaders.Names.DATE));
            response.headers().set(RtspHeaders.Names.CONTENT_LENGTH, response.content().readableBytes());
            response.headers().set(RtspHeaders.Names.CONTENT_TYPE, "application/sdp");
        } 
        else if (RtspMethods.ANNOUNCE.equals(method)) {
            String sdp = request.content().toString(Charset.forName("UTF-8"));
            logger.info("sdp\r\n{}", sdp);
            
            session = new RtspSession(request.getUri())
                            .withSdp(sdp)
                            .withMode(SessionMode.PUBLISH);
            response = makeResponse(request, session);
        }
        else if (RtspMethods.SETUP.equals(method)) {
            try {
                String exceptTransport = request.headers().get(RtspHeaders.Names.TRANSPORT);
                String transport = session.setupStream(request.getUri(), exceptTransport);

                response = makeResponse(request, session);
                response.headers().add(RtspHeaders.Names.CACHE_CONTROL, RtspHeaders.Values.NO_CACHE);
                response.headers().add(RtspHeaders.Names.EXPIRES, response.headers().get(RtspHeaders.Names.DATE));
                response.headers().add(RtspHeaders.Names.TRANSPORT, transport);
            } catch (TransportNotSupportedException ex) {
                logger.warn("Not Supported Transport '{}'", ex.getMessage(), ex);
                
                response = makeResponse(request, session);
                response.setStatus(HttpResponseStatus.NOT_EXTENDED);
            }
        }
        else if (RtspMethods.RECORD.equals(method)) {
            response = makeResponse(request, session);
            response.headers().set(RtspHeaders.Names.RTP_INFO,  getRtpInfo(request));
            
            session.record();
        }
        else if (RtspMethods.PLAY.equals(method)) {
            session.play();
            
            // response = makeResponse(request, null);
            response = makeResponse(request, session);
            response.headers().set(RtspHeaders.Names.RTP_INFO,  getRtpInfo(request));
        }
        else if (RtspMethods.GET_PARAMETER.equals(method)) {
            response = makeResponse(request, session);
        }
        else if (RtspMethods.TEARDOWN.equals(method)) {
            if (null != session) {
                session.destroy();
                session = null;
            }
            
            response = makeResponse(request, session);
        }
        // OTHERWISE
        else {
            logger.warn("unsupported method [{}]", method);
            logger.debug("{}", request.content().toString(Charset.forName("UTF-8")));
            

            response = makeResponse(request, session);
            response.setStatus(HttpResponseStatus.FORBIDDEN);
        }
        
        
        if (null != response) {
            ctx.writeAndFlush(response);
        }
    }

    private String getRtpInfo(HttpRequest request) {
        String url = request.getUri();
        String baseUrl = url.substring(0, url.indexOf('/', "rtsp://".length()));
        // baseUrl = "rtsp://54.223.242.201:1554";
        
        StringBuilder rtpInfo = new StringBuilder();
        for (int i = 0; i < setups.size(); i++) {
            if (i != 0) {
                rtpInfo.append(",");
            }
            rtpInfo.append(setups.get(i).getUrl(baseUrl));
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
        resp.headers().set(RtspHeaders.Names.SERVER, "Sengled Media Server On Netty");

        return resp;
    }
}
