package com.sengled.cloud.mediaserver;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.rtsp.RtspHeaders;
import io.netty.handler.codec.rtsp.RtspMethods;
import io.netty.handler.codec.rtsp.RtspResponseStatuses;
import io.netty.handler.codec.rtsp.RtspVersions;
import io.netty.handler.timeout.IdleStateEvent;

import java.io.Closeable;
import java.io.IOException;
import java.net.ConnectException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;

import javax.sip.TransportNotSupportedException;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sengled.cloud.mediaserver.rtsp.RtspSession;
import com.sengled.cloud.mediaserver.rtsp.RtspSession.SessionMode;
import com.sengled.cloud.mediaserver.rtsp.Transport;
import com.sengled.cloud.mediaserver.rtsp.rtp.RTCPContent;
import com.sengled.cloud.mediaserver.rtsp.rtp.RTPContent;
import com.sengled.cloud.mediaserver.url.URLObject;

/**
 * 从其他的 rtsp 流中拉取视频数据，
 * <p>
 * 通过  {@link #connect()} 方法，完成 rtsp 协商. 
 * @author 陈修恒
 * @date 2016年4月15日
 */
public class RtspClient implements Closeable {
    private static final Logger logger = LoggerFactory.getLogger(RtspClient.class);

    private int seqNo = 1;
    private String name;
    private URLObject urlObj;

    private RtspSession session;
    private Channel channel;

    private boolean isConnected;
    private Semaphore connectOrFail = new Semaphore(0);
    private AtomicReference<Throwable> error = new AtomicReference<Throwable>();

    private HttpMethod requestMethod;
    private String requestUrl;
    private HttpHeaders requestHeaders;
    
    private boolean isClosed;

    public RtspClient(String name, URLObject urlObj, Channel channel) {
        super();
        this.name = null != name ? name : urlObj.getUri();
        this.urlObj = urlObj;
        this.channel = channel;
    }

    public ChannelHandler getRtspResponseHandler() {
        return new RtspClientInboundResponseHandler();
    }

    @Override
    protected void finalize() throws Throwable {
        close();
    }

    @Override
    public void close() throws IOException {
        isClosed = true; 
        writeAndFlush(channel, makeRequest(RtspMethods.TEARDOWN));
    }



    public void connect() throws IOException, InterruptedException {
        writeAndFlush(channel, makeRequest(RtspMethods.OPTIONS));

        connectOrFail.acquire();
        Throwable throwable = error.get();
        if (throwable instanceof IOException) {
            throw (IOException) throwable;
        } else if (throwable instanceof Exception) {
            logger.info("{}", throwable.getMessage(), throwable);
            throw new ConnectException(throwable.toString());
        } else {
            isConnected = true;
        }
    }

    private void writeAndFlush(Channel channel,
                               HttpRequest request) {
        if (null != request && channel.isOpen() && !isClosed) {
            channel.writeAndFlush(request);
            RtspClient.this.requestUrl = request.getUri();
            RtspClient.this.requestMethod = request.getMethod();
            RtspClient.this.requestHeaders = request.headers();
        }
        
        if (RtspMethods.TEARDOWN.equals(requestMethod)) {
            // 标记为已经关闭
            if (channel.isOpen()) {
                channel.close();
            }
        } 
    }

    
    private FullHttpRequest makeRequest(HttpMethod method) {
        return makeRequest(method, urlObj.getUrl());
    }

    private FullHttpRequest makeRequest(HttpMethod method,
                                        String url) {
        FullHttpRequest request = new DefaultFullHttpRequest(RtspVersions.RTSP_1_0, method, url);
        request.headers().add(RtspHeaders.Names.CSEQ, seqNo++);
        request.headers().add(RtspHeaders.Names.USER_AGENT, "Sengled Rtsp Client");
        request.headers().add(RtspHeaders.Names.CONTENT_LENGTH, 0);

        if (null != session && null != session.getId()) {
            request.headers().add(RtspHeaders.Names.SESSION, session.getId());
        }


        return request;
    }

    private HttpMethod getRequestMethod() {
        return requestMethod;
    }

    public String getRequestUrl() {
        return requestUrl;
    }

    private class RtspClientInboundResponseHandler extends ChannelInboundHandlerAdapter {
        private boolean isAuth;
        private boolean supportGetParameter;
        
        @Override
        public void userEventTriggered(ChannelHandlerContext ctx,
                                       Object evt) throws Exception {
            super.userEventTriggered(ctx, evt);

            if (evt instanceof IdleStateEvent) {
                HttpRequest requst = null;
                IdleStateEvent idle = (IdleStateEvent)evt;
                switch (idle.state()) {
                    case READER_IDLE:
                        requst = makeRequest(RtspMethods.TEARDOWN);
                        logger.warn("read timeout, tear down. url = {}", urlObj);
                        break;
                    case WRITER_IDLE:
                        if (supportGetParameter) {
                            requst = makeRequest(RtspMethods.GET_PARAMETER);
                        } else {
                            requst = makeRequest(RtspMethods.OPTIONS);
                        }
                        break;
                    case ALL_IDLE:
                    default:
                        break;
                }
                

                writeAndFlush(channel, requst);
            }
        }
        
        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            super.channelInactive(ctx);

            if (!isClosed) {
                error.compareAndSet(null, new IOException("channel closed"));
            }
            connectOrFail.release();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx,
                                    Throwable cause) throws Exception {
            error.compareAndSet(null, cause);

            // send teardown
            HttpRequest request = makeRequest(RtspMethods.TEARDOWN);
            ctx.writeAndFlush(request).sync();

            // close channel
            ctx.channel().close();
        }


        @Override
        public void channelRead(ChannelHandlerContext ctx,
                                Object msg) throws Exception {
            if (msg instanceof FullHttpResponse) {
                handleHttpResponse(ctx, (FullHttpResponse) msg);
            } else if (msg instanceof RTPContent) {
                handleRtpPacket(msg);
            } else if (msg instanceof RTCPContent) {
                // logger.debug("{}", msg);
            } else {
                logger.warn("what's this '{}'?", msg);
            }
        }

        private void handleRtpPacket(Object msg) {
            if (null != session) {
                session.dispatch((RTPContent) msg);
            }
        }

        protected void handleHttpResponse(ChannelHandlerContext ctx,
                                          FullHttpResponse response) throws Exception {
            final HttpMethod method = getRequestMethod();
            int code = response.getStatus().code();

            HttpRequest request = null;
            if (200 == code) {
                logger.info("{}, {} {}", response.getStatus(), requestMethod, requestUrl);
                request = nextRequest(response);
            } else if (RtspResponseStatuses.UNAUTHORIZED.equals(response.getStatus())) {
                if (isAuth) {
                    throw new AuthorizedException(urlObj.getUser(), urlObj.getPassword());
                }
                
                String auth = getAuthorizationString(response);
                if (null != auth) {
                    request = makeRequest(method, requestUrl);
                    request.headers().add(requestHeaders.remove(RtspHeaders.Names.CSEQ));
                    request.headers().add(RtspHeaders.Names.AUTHORIZATION, auth);
                } else {
                    throw new AuthorizedException(urlObj.getUser(), urlObj.getPassword());
                }

                isAuth = true; // 标记为已经验证过了，避免重复验证
                logger.info("send authorized again");
            } else if (400 <= code && code < 500) {
                throw new StreamNotFoundException(urlObj.getUrl());
            } else  {
                throw new ConnectException(response.getStatus() + " when call " + method + " " + requestUrl);
            }

            if (null != request && ctx.channel().isActive()) {
                logger.info("send, {} {}", request.getMethod(), request.getUri());
                if (logger.isDebugEnabled()) {
                    for (Entry<String, String> keyValue : request.headers().entries()) {
                        logger.debug("{}:{}", keyValue.getKey(), keyValue.getValue());
                    }
                    logger.debug("<<");
                }
                writeAndFlush(ctx.channel(), request);
            }
        }

 
        private HttpRequest nextRequest(FullHttpResponse response
                                            )
                throws TransportNotSupportedException {
            final HttpMethod requestMethod = getRequestMethod();
            final String requestUrl = getRequestUrl();

            HttpRequest request = null;
            if (RtspMethods.OPTIONS.equals(requestMethod)) {
                String supports = response.headers().get(RtspHeaders.Names.PUBLIC);
                supportGetParameter = StringUtils.contains(supports, RtspMethods.GET_PARAMETER.name());

                if (!isConnected) {
                    request = makeRequest(RtspMethods.DESCRIBE);
                    request.headers().add(RtspHeaders.Names.ACCEPT, "application/sdp");
                } else {
                    // 已经连上了，可能是在发心跳
                }
            } else if (RtspMethods.DESCRIBE.equals(requestMethod)) {
                String sessionId = response.headers().get(RtspHeaders.Names.SESSION);
                session = new RtspSession(urlObj.getUrl(), sessionId, name);
                session.withMode(SessionMode.PUBLISH)
                        .withSdp(response.content().toString(Charset.forName("UTF-8")));

                request = setupStream(0);
            } else if (RtspMethods.SETUP.equals(requestMethod)) {
                int streamIndex = session.getStreamIndex(requestUrl);
                String transport = response.headers().get(RtspHeaders.Names.TRANSPORT);

                session.setupStream(requestUrl, transport);
                session.setId(response.headers().get(RtspHeaders.Names.SESSION));

                request = setupStream(streamIndex + 1);
            } else if (RtspMethods.PLAY.equals(requestMethod)) {
                session.play();
                connectOrFail.release(); // 握手成功
                isConnected = true;
            } else if (RtspMethods.TEARDOWN.equals(requestMethod)) {
                if (null != session) {
                    session.destroy();
                }
            } else if (RtspMethods.GET_PARAMETER.equals(requestMethod)) {
                
            }
            return request;
        }

        private HttpRequest setupStream(int streamIndex) {
            HttpRequest request;
            if (streamIndex < session.numStreams()) {
                logger.info("setup stream {}/{}", streamIndex, session.numStreams());
                String url = urlObj.getUrl(session.getStreamUri(streamIndex));
                request = makeRequest(RtspMethods.SETUP, url);

                request.headers().add(RtspHeaders.Names.TRANSPORT,
                        Transport.rtpOnTcp(streamIndex * 2 + 0, streamIndex * 2 + 1));
            } else {
                request = makeRequest(RtspMethods.PLAY);
                request.headers().add(RtspHeaders.Names.RANGE, "npt=0.000-");
            }

            return request;
        }


        private String getAuthorizationString(HttpResponse request) {
            List<String> auths = request.headers().getAll("WWW-Authenticate");
            if (null != auths) {
                for (String auth : auths) {
                    if (StringUtils.startsWith(auth, "Basic")) {
                        String user = urlObj.getUser();
                        String pass = urlObj.getPassword();
                        byte[] bytes =
                                org.apache.commons.codec.binary.Base64.encodeBase64(new String(user
                                        + ":"
                                        + (pass != null ? pass : "")).getBytes());
                        String authValue = "Basic " + new String(bytes);
                        return authValue;

                    }
                }
            }

            return null;
        }


    }
}
