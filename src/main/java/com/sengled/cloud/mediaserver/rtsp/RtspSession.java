package com.sengled.cloud.mediaserver.rtsp;

import gov.nist.core.StringTokenizer;
import gov.nist.javax.sdp.SessionDescriptionImpl;
import gov.nist.javax.sdp.fields.SDPField;
import gov.nist.javax.sdp.parser.ParserFactory;
import gov.nist.javax.sdp.parser.SDPParser;

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

import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sengled.cloud.mediaserver.event.Listener;
import com.sengled.cloud.mediaserver.rtsp.rtp.RTPContent;
import com.sengled.cloud.mediaserver.rtsp.rtp.RTPStream;

/**
 * 一个 rtsp 会话。
 * 
 * <p>
 * <ul>
 * <li>1、通过 {@link #dispatch(RTPContent)} 把流数据分发给他的监听者</li>
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
    private Listener listener;

    private SessionDescription sd;
    private RTPStream[] streams;
    
    public RtspSession(String url) {
        this(getUri(url), RandomStringUtils.random(16, false, true));
    }
    
    public RtspSession(String url, String sessionId) {
        this(url, sessionId, getUri(url));
    }

    public RtspSession(String url, String sessionId, String name) {
        this.id = sessionId;
        this.uri = getUri(url);
        this.name = name;
    }
    
    
    public String setupStream(String url, String transport) throws TransportNotSupportedException {
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
                    return t.toString();
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
            return uri + "/" + medias.get(index).getAttribute("control");
        } catch (SdpParseException e) {
            logger.error("MediaDescription has error on stream[" + index + "], sdp = " + sd);
        }
        
        return null;
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
            uri = url.substring(url.indexOf("/", "rtsp://".length()));
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
        return this;
    }

    
    public RtspSession withListener(Listener listener) {
        // remove old
        Sessions.getInstance().unregister(uri, this.listener);
        
        // use new
        this.listener = listener;
        return this;
    }
    
    public RtspSession withMode(SessionMode newMode) {
        Sessions sessions = Sessions.getInstance();
        switch (newMode) {
            case PLAY:
                this.sd = sessions.getSessionDescription(uri);
                this.streams = new RTPStream[getMediaDescriptions(sd).size()];
                break;
            case PUBLISH:
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

    public void destroy() {
        Sessions sessions = Sessions.getInstance();
        
        switch (mode) {
            case PUBLISH:
                sessions.removeSession(name, this);
                break;
            case PLAY:
                Sessions.getInstance().unregister(name, listener);
            default:
                break;
        }
    }
    

    public void dispatch(RTPContent msg) {
        if (mode == SessionMode.PUBLISH && streams.length > 0) {
            int channel = msg.getChannel();

            // 分发
            for (int streamIndex = 0; null != streams && streamIndex < streams.length; streamIndex++) {
                if (streams[streamIndex].getRtpChannel() == channel) {
                    streams[streamIndex].dispatch(name, streamIndex, msg);
                    break;
                }
            }
            
            /* 匹配时间戳
            int numStreams = numStreams();
            if (numStreams == 2 
                    && null != streams[0] && streams[0].isStarted() 
                    && null != streams[1] && streams[1].isStarted()) {
                long t0 = streams[0].getTimestampMillis();
                long t1 = streams[1].getTimestampMillis();
                long delay = t0 - t1;
                if (delay > 500) {
                    logger.info(" stream#0 fast {}ms then stream#1", delay);
                    streams[1].setTimestampMillis(t0);
                } else if(delay < - 500) {
                    logger.info(" stream#0 late {}ms then stream#1", -delay);
                    streams[0].setTimestampMillis(t1);
                } else {
                    logger.trace("delay is {}ms between stream#0 and stream#1", delay);
                }
            }*/
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
