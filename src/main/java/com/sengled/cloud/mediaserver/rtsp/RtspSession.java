package com.sengled.cloud.mediaserver.rtsp;

import gov.nist.core.StringTokenizer;
import gov.nist.javax.sdp.SessionDescriptionImpl;
import gov.nist.javax.sdp.fields.SDPField;
import gov.nist.javax.sdp.parser.ParserFactory;
import gov.nist.javax.sdp.parser.SDPParser;

import java.io.Serializable;
import java.text.ParseException;

import javax.sdp.SessionDescription;

import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sengled.cloud.mediaserver.rtsp.codec.InterleavedFrame;
import com.sengled.cloud.mediaserver.rtsp.mq.RtspListener;

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

    final private String id;
    private String uri;
    private SessionDescription sdp;
    private SessionMode mode = SessionMode.OTHERS;
    private RtspListener listener;
    
    public RtspSession(String url) {
        this.id = RandomStringUtils.random(16, false, true);
        this.uri = getUri(url);
    }

    public RTPSetup setupStream(String url) {
        //TODO setup streams
        return new RTPSetup(getUri(url));
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
        
        this.sdp = sd;
        return this;
    }
    
    public RtspSession withListener(RtspListener listener) {
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
                this.sdp = sessions.getSdp(uri);
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
                sessions.updateSdp(uri, getSdp());
                break;
            case PLAY:
                Sessions.getInstance().register(uri, listener);
                break;
            default:
                break;
        }
    }

    public void destroy() {
        Sessions sessions = Sessions.getInstance();
        
        switch (mode) {
            case PUBLISH:
                sessions.removeSdp(uri, getSdp());
                break;
            case PLAY:
                Sessions.getInstance().unregister(uri, listener);
            default:
                break;
        }
    }
    

    public void dispatch(InterleavedFrame msg) {
        if (mode == SessionMode.PUBLISH) {
            Sessions.getInstance().dispatch(uri, msg);
        }
    }
    
    public String getUri() {
        return uri;
    }
    
    public SessionMode getState() {
        return mode;
    }
    
    public SessionDescription getSdp() {
        return sdp;
    }
    
    
    public String getId() {
        return id;
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
