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
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.nio.charset.Charset;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicLong;

import javax.sdp.SessionDescription;
import javax.sip.TransportNotSupportedException;

import org.slf4j.LoggerFactory;

import com.sengled.cloud.mediaserver.event.Event;
import com.sengled.cloud.mediaserver.event.Listener;
import com.sengled.cloud.mediaserver.rtsp.RTPSetup;
import com.sengled.cloud.mediaserver.rtsp.RtspSession;
import com.sengled.cloud.mediaserver.rtsp.RtspSession.SessionMode;
import com.sengled.cloud.mediaserver.rtsp.Sessions;
import com.sengled.cloud.mediaserver.rtsp.codec.InterleavedFrame;
import com.sengled.cloud.mediaserver.rtsp.rtp.RTPContent;
import com.sengled.cloud.mediaserver.rtsp.rtp.RtpEvent;
import com.sengled.cloud.mediaserver.url.URLObject;

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

    
    private RtspSession session = null;
    private List<RTPSetup> setups = new ArrayList<RTPSetup>();
    
    private AtomicLong numRtp = new AtomicLong();
    
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);

        logger.info("open <{}, {}>", ctx.channel().remoteAddress(), ctx.channel().localAddress());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (null != session) {
            session.destroy();
        }
        
        super.channelInactive(ctx);

        logger.info("close <{}, {}>", ctx.channel().remoteAddress(), ctx.channel().localAddress());
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx,
                                   Object evt) throws Exception {
        super.userEventTriggered(ctx, evt);
        
        if (evt instanceof IdleStateEvent) {
            if (null != session && session.getMode() == SessionMode.PUBLISH) {
                throw new java.util.concurrent.TimeoutException("TimeOut To ReadOrWrite Data");
            }
        }
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
            if (null != session && frame instanceof RTPContent) {
                session.dispatch((RTPContent)frame.retain());
            } else {
                logger.debug("{}", frame);
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
                .withListener(new RtspEventListener(session, ctx, 1024));
            
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
            
        }
        else if (RtspMethods.PLAY.equals(method)) {
            // response = makeResponse(request, null);
            response = makeResponse(request, session);
            response.headers().set(RtspHeaders.Names.RTP_INFO,  getRtpInfo(request));
        }
        else if (RtspMethods.GET_PARAMETER.equals(method)) {
            try {
                URLObject urlObj = new URLObject(request.getUri());
                SessionDescription  sd = Sessions.getInstance().getSessionDescription(urlObj.getUri());
                
                if (null == sd) {
                    response = makeResponse(request, session);
                    response.setStatus(HttpResponseStatus.NOT_FOUND);
                } else {
                    response = makeResponse(request, session);
                }
            } catch (MalformedURLException ex) {
                response = makeResponse(request, session);
                response.setStatus(HttpResponseStatus.BAD_REQUEST);
                logger.warn("request url decode failed. {}", ex.getMessage(), ex);
            }
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
            
            if (RtspMethods.RECORD.equals(method)) {
                session.record();
            } else if (RtspMethods.PLAY.equals(method)) {
                session.play();
            }
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
    
    public static class RtspEventListener implements Listener, GenericFutureListener<Future<? super Void>> {
        final private AtomicLong rtpBufferSize = new AtomicLong();
        final private RtspSession mySession;
        final private ChannelHandlerContext ctx ;
        final private int maxRtpBufferSize;
        
        public RtspEventListener(RtspSession mySession, ChannelHandlerContext ctx, int maxRtpBufferSize) {
            super();
            this.ctx = ctx;
            this.mySession = mySession;
            this.maxRtpBufferSize = maxRtpBufferSize;
        }


        @Override
        public void on(Event event) {
            try {
                if(event instanceof RtpEvent) {
                    RtpEvent rtp = ((RtpEvent)event);

                    onRtpEvent(rtp);
                }
            } finally {
                ReferenceCountUtil.release(event);
            }
        }


        private void onRtpEvent(RtpEvent rtp) {
            int streamIndex = rtp.getStreamIndex();
            ByteBuf content = rtp.content();
            int payloadLength = content.readableBytes();
            
            if (mySession.isStreamSetup(streamIndex)) {
                int channel = mySession.getStreamRTPChannel(streamIndex);
                
                ByteBuf payload = ctx.alloc().buffer(4 + payloadLength);
                payload.writeByte('$');
                payload.writeByte(channel);
                payload.writeShort(payloadLength);
                payload.writeBytes(content);

                long bufferSize = rtpBufferSize.incrementAndGet();
                if (!bufferIfFull(bufferSize)) {
                    // 当成功发送数据包后， 更新计数器
                    ctx.writeAndFlush(payload, ctx.newPromise().addListener(this));
                } else {
                    // 已经有很多数据没有发送了， 可能客户端网络不好，直接丢包
                    rtpBufferSize.decrementAndGet();

                    ctx.fireExceptionCaught(new BufferOverflowException("limit is " + maxRtpBufferSize + ", but real is" + bufferSize));
                }
            }
        }


        private boolean bufferIfFull(long bufferSize) {
            return maxRtpBufferSize > 0 && bufferSize > maxRtpBufferSize;
        }

        /**
         * 监听消息是否成功发送出去了
         */
        @Override
        public void operationComplete(Future<? super Void> future) throws Exception {
            long bufferSize = rtpBufferSize.decrementAndGet();
            if (bufferIfFull(bufferSize)) {
                ctx.fireExceptionCaught(new BufferOverflowException("limit is " + maxRtpBufferSize + ", but real is" + bufferSize));
            }

            logger.trace("rtp buffer size = {}", bufferSize);
        }

    }
}
