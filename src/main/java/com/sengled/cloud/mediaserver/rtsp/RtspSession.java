package com.sengled.cloud.mediaserver.rtsp;

import gov.nist.core.StringTokenizer;
import gov.nist.javax.sdp.SessionDescriptionImpl;
import gov.nist.javax.sdp.fields.SDPField;
import gov.nist.javax.sdp.parser.ParserFactory;
import gov.nist.javax.sdp.parser.SDPParser;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.rtsp.RtspHeaders;
import io.netty.util.ReferenceCountUtil;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Vector;

import javax.sdp.MediaDescription;
import javax.sdp.SdpException;
import javax.sdp.SdpParseException;
import javax.sdp.SessionDescription;
import javax.sip.TransportNotSupportedException;

import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sengled.cloud.mediaserver.rtsp.ServerEngine.Dispatcher;
import com.sengled.cloud.mediaserver.rtsp.interleaved.RtcpContent;
import com.sengled.cloud.mediaserver.rtsp.interleaved.RtpPkt;
import com.sengled.cloud.mediaserver.rtsp.rtp.InterLeavedRTPSession;
import com.sengled.cloud.mediaserver.url.URLObject;

/**
 * 一个 rtsp 会话。
 * 
 * <p>
 * <ul>
 * <li>1、通过 {@link #onRtpEvent(RtpPkt)} 把流数据分发给他的监听者</li>
 * <li>2、通过 {@link #getSessionDescription()} 获取 SDP 信息</li>
 * </ul>
 * @author 陈修恒
 * @date 2016年4月15日
 */
public class RtspSession  {
    private static final Logger logger = LoggerFactory.getLogger(RtspSession.class);

    public enum SessionMode {
        /** PUBLISH: 客户端向服务器推流 */
        PUBLISH,
        /** PLAY: 客户端从服务器拉流 */
        PLAY,
        
        /** 其他 */
        OTHERS
    }

    final private ServerEngine engine;
    final private ChannelHandlerContext ctx;

    private String id;
    private String name;
    private String uri;
    
    private SessionMode mode = SessionMode.OTHERS;
    private PlayState state = PlayState.WAITING;
    
    private String userAgent;
    private SessionDescription sd;
    private InterLeavedRTPSession[] rtpSessions = null;
    
    private RtspSessionListener listener;
    private RtspSessionDispatcher dispatcher;
    
    public RtspSession(ServerEngine engine, ChannelHandlerContext ctx, String url) {
        this(engine, ctx, URLObject.getUri(url), RandomStringUtils.random(16, false, true));
    }
    
    public RtspSession(ServerEngine engine, ChannelHandlerContext ctx, String url, String sessionId) {
        this(engine, ctx, url, sessionId, URLObject.getUri(url));
    }

    public RtspSession(ServerEngine engine, ChannelHandlerContext ctx, String url, String sessionId, String name) {
        this.engine = engine;
        this.ctx = ctx;
        this.id = sessionId;
        this.uri = URLObject.getUri(url);
        this.name = name;
    }
    

    
    public ServerEngine engine() {
        return engine;
    }
    
    public ChannelHandlerContext channelHandlerContext() {
        return ctx;
    }
    
    public Transport setupStream(String url, String transport) throws TransportNotSupportedException {
        Transport t = Transport.parse(transport);
        if (!StringUtils.equals(Transport.RTP_AVP_TCP, t.getTranport())) {
            throw new TransportNotSupportedException(t.getTranport());
        }
        
        if (!StringUtils.equals(Transport.UNICAST, t.getUnicast())) {
            throw new TransportNotSupportedException(t.getUnicast());
        }
        
        int[] interleaved = t.getInterleaved();
        if (null == interleaved) {
            throw new TransportNotSupportedException("interleaved");
        }
        
        String uri = URLObject.getUri(url);
        int mediaIndex = 0;
        for (MediaDescription dm : getMediaDescriptions(sd)) {
            try {
                if (StringUtils.endsWith(uri, getControlUri(dm))) {
                	MediaStream stream = new MediaStream(mediaIndex, dm, url);
                    rtpSessions[mediaIndex] = new InterLeavedRTPSession(stream, this, interleaved[0], interleaved[1]);
                    return t;
                }
            } catch (IllegalArgumentException ex) {
                logger.warn("{}", ex.getMessage(), ex);
            }
            
            mediaIndex ++;
        }

        throw new IllegalArgumentException("No Media Found in SDP, Named as '" + uri + "'");
    }

    
    private String getControlUri(MediaDescription dm) throws IllegalArgumentException {
        try {
            return URLObject.getUri(dm.getAttribute("control"));
        } catch (Exception c) {
            throw new IllegalArgumentException(c.getMessage(), c);
        }
    }


    public int numStreams() {
        return getMediaDescriptions(sd).size();
    }
    
    public String getStreamUri(int index) {
        java.util.List<MediaDescription> medias = getMediaDescriptions(sd);
        
        try {
        	if (StringUtils.endsWith(uri, "/")) {
        		return uri + medias.get(index).getAttribute("control");
        	} else {
        		return uri + "/" + medias.get(index).getAttribute("control");
        	}
        } catch (SdpParseException e) {
            logger.error("MediaDescription has error on stream[" + index + "], sdp = " + sd);
        }
        
        return null;
    }
    
    public InterLeavedRTPSession[] getRTPSessions() {
        return rtpSessions;
    }
    
    public int getStreamIndex(String url) {
        String uri = URLObject.getUri(url);
        int mediaIndex = 0;
        for (MediaDescription dm : getMediaDescriptions(sd)) {
            try {
                if (StringUtils.endsWith(uri, getControlUri(dm))) {
                    return mediaIndex;
                }
            } catch (IllegalArgumentException ex) {
                logger.warn("{}", ex.getMessage(), ex);
            }
            
            mediaIndex ++;
        }
        
        throw new IllegalArgumentException("stream[" + url + "] Not Found IN " + sd);
    }
    
    public int getStreamIndex(InterLeaved interLeaved) {
        int channel = interLeaved.channel();

        return getStreamIndex(channel);
    }

    public int getStreamIndex(int channel) {
        for (int i = 0; i < rtpSessions.length; i++) {
            if (null == rtpSessions[i] ) {
                continue;
            }

            if (rtpSessions[i].rtcpChannel() == channel
                    || rtpSessions[i].rtpChannel() == channel) {
                return i;
            }
        }
        
        return -1;
    }

    
    @SuppressWarnings("unchecked")
    private java.util.List<MediaDescription> getMediaDescriptions(SessionDescription sd) {
        try {
            if (null != sd) {
                @SuppressWarnings("rawtypes")
                Vector vector = sd.getMediaDescriptions(false);
                if (null != vector) {
                    return new ArrayList<MediaDescription>(vector);
                }
            }
        } catch (SdpException e) {
            logger.warn("fail get getMediaDescriptions from {}, {}", uri, e.getMessage(), e);
        }

        return Collections.emptyList();
    }
    
    public RtspSession withUserAgent(HttpHeaders headers) {
        this.userAgent = headers.get(RtspHeaders.Names.USER_AGENT);
        return this;
    }
    
    public RtspSession withSdp(String sdp) {
        SessionDescriptionImpl sd = new SessionDescriptionImpl();

        if (!StringUtils.isEmpty(sdp)) {
            StringTokenizer tokenizer = new StringTokenizer(sdp);
            while (tokenizer.hasMoreChars()) {
                String line = tokenizer.nextToken();

                try {
                    SDPParser paser = ParserFactory.createParser(line);
                    if (null != paser) {
                        SDPField obj = paser.parse();
                        sd.addField(obj);
                    }
                } catch (ParseException e) {
                    logger.warn("fail parse [{}]", line, e);
                }
            }
            
        }
    
        List<MediaDescription> mediaDescripts = getMediaDescriptions(sd);

        this.sd = sd;
        this.rtpSessions = new InterLeavedRTPSession[mediaDescripts.size()];
        return this;
    }
    
    public void record() {
        this.play();
    }

    public void play() {
        switch (mode) {
            case PUBLISH:
                Dispatcher dispatcher = engine.putSession(name, this);
                this.dispatcher = new RtspSessionDispatcher(dispatcher, this);
                logger.info("{} will publish media", userAgent);
                break;
            case PLAY: 
                this.listener = new RtspSessionListener(this, 128); 
                int numListeners = engine.register(name, listener);
                logger.info("{} is {}th listener of '{}'", userAgent, numListeners, name);
                break;
            default:
                break;
        }
    }

    public RtspSession withMode(SessionMode newMode) {
        switch (newMode) {
            case PLAY:
                this.sd = engine.getSessionDescription(name);
                this.rtpSessions = new InterLeavedRTPSession[getMediaDescriptions(sd).size()];
               
                break;
            case PUBLISH:
                break;
            default:
                break;
        }

        this.mode = newMode;
        return this;
    }
    
 
    public void destroy(String reason) {
        logger.debug("destroy, mode = {}", mode);
		if (state() != PlayState.END) {
            switch (mode) {
    		case PUBLISH:
    			engine.removeSession(name, this);
    			break;
    		case PLAY:
    			engine.unregister(name, listener());
    			for (int i = 0; i < numStreams(); i++) {
    				if (null != rtpSessions[i]) {
    					rtpSessions[i].endSession(reason);
    				}
    			}
    			
    		default:
    			break;
    		}
		}

		state(PlayState.END);
    }
    
    public void close() {
    	ctx.close();
    }
    
    public boolean isDestroyed() {
        return state() == PlayState.END;
    }

    
    public String getUri() {
        return uri;
    }
    
    public SessionMode getState() {
        return mode;
    }
    
    public SessionDescription getSessionDescription() {
        return sd;
    }
    
    public String getSDP() {
        return null == sd ? null : sd.toString();
    }
    
    
    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }
    
    public boolean isStreamSetup(int streamIndex) {
        if (streamIndex < 0 || streamIndex >= rtpSessions.length) {
            return false;
        }

        return null != rtpSessions[streamIndex];
    }


    public void setId(String id) {
        this.id = id;
    }
    
    
    public SessionMode getMode() {
        return mode;
    }


    public void onRtcpEvent(RtcpContent event) {
        try {
            if (SessionMode.PUBLISH == mode) {
                dispatcher().receiveRtcpEvent(event.retain());
            } else { 
                listener().receiveRtcpEvent(event.retain());
            }
        } finally {
            // 保证在  dispatcher(), listener() 发生异常时，
            // 能够正常释放内存
            ReferenceCountUtil.release(event);
        }
    }
    
    public RtspSessionDispatcher dispatcher() {
    	return dispatcher;
    }
    
    
    public RtspSessionListener listener() {
    	return listener;
    }
  
    
    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append("{RtspSession");
        buf.append(", id=").append(id);
        buf.append(", state=").append(state());
        buf.append(", uri=").append(uri);
        buf.append(", ").append(mode);
        
        buf.append("}");
        return buf.toString();
    }

    public boolean hasVideo() {
        for (int i = 0; i < rtpSessions.length; i++) {
        	if (null == rtpSessions[i]) {
        		continue;
        	}

        	MediaStream s = (MediaStream)rtpSessions[i].getMediaStream();
            if (null != s && s.isVideo()) {
                return true;
            }
        }
        return false;
    }

    public PlayState state() {
        return state;
    }
    
    public void state(PlayState newState) {
        PlayState oldState = this.state;
        this.state = newState;
        
        if (oldState != newState) {
            logger.info("Playing {}, {}", newState, this);
        }

        if (newState == PlayState.WAITING) {
            InterLeavedRTPSession[] rtpSessions = getRTPSessions();
            for (int i = 0; i < rtpSessions.length; i++) {
                InterLeavedRTPSession subSession = rtpSessions[i];
                if (null != subSession) {
                    subSession.await();
                }
            }
        }
    }
}
