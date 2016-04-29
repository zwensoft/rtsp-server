package com.sengled.cloud.mediaserver.rtsp;

import gov.nist.core.StringTokenizer;
import gov.nist.javax.sdp.SessionDescriptionImpl;
import gov.nist.javax.sdp.fields.SDPField;
import gov.nist.javax.sdp.parser.ParserFactory;
import gov.nist.javax.sdp.parser.SDPParser;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;

import java.io.Serializable;
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

import jlibrtp.tcp.InterLeavedRTPSession;

import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sengled.cloud.mediaserver.rtsp.rtp.RTPStream;
import com.sengled.cloud.mediaserver.rtsp.rtp.RtcpContent;
import com.sengled.cloud.mediaserver.rtsp.rtp.RtpPkt;

/**
 * 一个 rtsp 会话。
 * 
 * <p>
 * <ul>
 * <li>1、通过 {@link #onRtcpEvent(RtpPkt)} 把流数据分发给他的监听者</li>
 * <li>2、通过 {@link #getSessionDescription()} 获取 SDP 信息</li>
 * </ul>
 * @author 陈修恒
 * @date 2016年4月15日
 */
public class RtspSession implements Serializable {
    private static final Logger logger = LoggerFactory.getLogger(RtspSession.class);
    private static final long serialVersionUID = -8562791602891803122L;

    public enum SessionMode {
        /** PUBLISH: 客户端向服务器推流 */
        PUBLISH,
        /** PLAY: 客户端从服务器拉流 */
        PLAY,
        
        /** 其他 */
        OTHERS
    }

    private String id;
    private String name;
    private String uri;
    private SessionMode mode = SessionMode.OTHERS;
    final private ChannelHandlerContext ctx;

    private SessionDescription sd;
    private RTPStream[] streams;
    private InterLeavedRTPSession[] rtpSessions = null;
    
    private RtspSessionListener listener;
    private RtspSessionDispatcher dispatcher;
    
    public RtspSession(ChannelHandlerContext ctx, String url) {
        this(ctx, getUri(url), RandomStringUtils.random(16, false, true));
    }
    
    public RtspSession(ChannelHandlerContext ctx, String url, String sessionId) {
        this(ctx, url, sessionId, getUri(url));
    }

    public RtspSession(ChannelHandlerContext ctx, String url, String sessionId, String name) {
        this.ctx = ctx;
        this.id = sessionId;
        this.uri = getUri(url);
        this.name = name;
    }
    
    public Channel channel() {
        return ctx.channel();
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
        
        String uri = getUri(url);
        int mediaIndex = 0;
        for (MediaDescription dm : getMediaDescriptions(sd)) {
            try {
                if (StringUtils.endsWith(uri, getUri(dm.getAttribute("control")))) {
                    streams[mediaIndex] = new RTPStream(mediaIndex, dm, interleaved[0], interleaved[1]);
                    rtpSessions[mediaIndex] = new InterLeavedRTPSession(ctx.channel(), interleaved[0], interleaved[1]);
                    return t;
                }
            } catch (SdpParseException ex) {
                logger.warn("{}", ex.getMessage(), ex);
            }
            
            mediaIndex ++;
        }

        throw new IllegalArgumentException("No Media Found in SDP, Named as '" + uri + "'");
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
    
    public RTPStream[] getStreams() {
        return streams;
    }
    
    public InterLeavedRTPSession[] getRTPSessions() {
        return rtpSessions;
    }
    
    public int getStreamIndex(String url) {
        String uri = getUri(url);
        int mediaIndex = 0;
        for (MediaDescription dm : getMediaDescriptions(sd)) {
            try {
                if (StringUtils.endsWith(uri, getUri(dm.getAttribute("control")))) {
                    return mediaIndex;
                }
            } catch (SdpParseException ex) {
                logger.warn("{}", ex.getMessage(), ex);
            }
            
            mediaIndex ++;
        }
        
        throw new IllegalArgumentException("stream[" + url + "] Not Found IN " + sd);
    }
    
    
    
    public InterLeavedRTPSession getRTPSessionByChannel(int channel) {
        for (int i = 0; i < streams.length; i++) {
            if (null == streams[i] ) {
                continue;
            }

            if (streams[i].getRtcpChannel() == channel
                    || streams[i].getRtpChannel() == channel) {
                return rtpSessions[i];
            }
        }
        
        
        return null;
    }
    
    public int getStreamIndex(InterLeaved interLeaved) {
        int channel = interLeaved.channel();

        for (int i = 0; i < streams.length; i++) {
            if (null == streams[i] ) {
                continue;
            }

            if (streams[i].getRtcpChannel() == channel
                    || streams[i].getRtpChannel() == channel) {
                return i;
            }
        }
        
        return -1;
    }
    public RTPStream getStream(String url) {
        String uri = getUri(url);
        int mediaIndex = 0;
        for (MediaDescription dm : getMediaDescriptions(sd)) {
            try {
                if (StringUtils.endsWith(uri, getUri(dm.getAttribute("control")))) {
                    return streams[mediaIndex];
                }
            } catch (SdpParseException ex) {
                logger.warn("{}", ex.getMessage(), ex);
            }
            
            mediaIndex ++;
        }
        
        throw new IllegalArgumentException("stream[" + url + "] Not Found IN " + sd);
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
    
    public static String getUri(String url) {
        String uri = url;
        if (StringUtils.startsWith(url, "rtsp://")) {
            int indexOf = url.indexOf("/", "rtsp://".length());
			uri = indexOf > 0 ? url.substring(indexOf) : "/";
        }
        return uri;
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
        this.streams = new RTPStream[mediaDescripts.size()];
        this.rtpSessions = new InterLeavedRTPSession[mediaDescripts.size()];
        return this;
    }
    
    public RtspSession withMode(SessionMode newMode) {
        Sessions sessions = Sessions.getInstance();
        switch (newMode) {
            case PLAY:
                this.sd = sessions.getSessionDescription(uri);
                this.streams = new RTPStream[getMediaDescriptions(sd).size()];
                this.rtpSessions = new InterLeavedRTPSession[getMediaDescriptions(sd).size()];
                this.listener = new RtspSessionListener(this, 128 * 1024); 
                break;
            case PUBLISH:
                this.dispatcher = new RtspSessionDispatcher(this);
                break;
            default:
                break;
        }

        this.mode = newMode;
        return this;
    }
    
    public void record() {
        this.play();
    }

    public void play() {
        Sessions sessions = Sessions.getInstance();
        
        switch (mode) {
            case PUBLISH:
                sessions.updateSession(name, this);
                break;
            case PLAY:
                Sessions.getInstance().register(name, listener);
                break;
            default:
                break;
        }
    }

    public void destroy(String reason) {
        Sessions sessions = Sessions.getInstance();
        
        switch (mode) {
            case PUBLISH:
                dispatcher().teardown(reason);
                sessions.removeSession(name, this);
                break;
            case PLAY:
                Sessions.getInstance().unregister(name, listener);
            default:
                break;
        }
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
        return null != streams[streamIndex];
    }

    public int getStreamRTPChannel(int streamIndex)  {
        return streams[streamIndex].getRtpChannel();
    }
    

    public void setId(String id) {
        this.id = id;
    }
    
    
    public SessionMode getMode() {
        return mode;
    }
 
    public int getStreamIndex(int channel) {
        for (int i = 0; i < rtpSessions.length; i++) {
            InterLeavedRTPSession s = rtpSessions[i];
            if (null != s && s.rtpChannel() == channel) {
                return i;
            }
            
            if (null != s && s.rtcpChannel() == channel) {
                return i;
            }
        }

        return -1;
    }


    public void onRtcpEvent(RtcpContent event) {
        if (SessionMode.PUBLISH == mode) {
            dispatcher().onRtcpEvent(event);
        } else { 
            listener().onRtcpEvent(event);
        }
    }
    
    public RtspSessionDispatcher dispatcher() {
        if (SessionMode.PUBLISH == mode) {
            return dispatcher;
        } else {
            throw new UnsupportedOperationException("SessionMode[" + mode + "] dont supporte Dispatcher");
        }
    }
    
    
    public RtspSessionListener listener() {
        if (SessionMode.PLAY == mode) {
            return listener;
        } else {
            throw new UnsupportedOperationException("SessionMode[" + mode + "] dont supporte Dispatcher");
        }
    }
    
    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append("{RtspSession#").append(id);
        buf.append(", uri=").append(uri);
        buf.append(", ").append(mode);
        
        buf.append("}");
        return buf.toString();
    }

}
