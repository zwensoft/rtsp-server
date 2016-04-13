package com.sengled.cloud.mediaserver.rtsp;

import java.io.Serializable;

import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.lang.StringUtils;

import com.sengled.cloud.mediaserver.rtsp.codec.InterleavedFrame;
import com.sengled.cloud.mediaserver.rtsp.mq.RtspListener;

public class RtspSession implements Serializable {
    private static final long serialVersionUID = -8562791602891803122L;

    public enum State {
        /** DESCRIBING: 客户端准备拉流 */
        DESCRIBING,
        /** ANNOUNCING: 客户端准备推流 */
        ANNOUNCING,
        /** PLAY: 正在播放 */
        PLAY,
        /** PLAY: 服务器正在录像 */
        RECORD,
        /** TEARDOWN: 连接断开  */
        TEARDOWN
    }

    final private String id;
    private String uri;
    private String sdp;
    private State currentState = State.ANNOUNCING;
    private boolean receiveFromClient;
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
        this.sdp = sdp;
        
        return this;
    }
    
    public RtspSession withListener(RtspListener listener) {
        // remove old
        Sessions.getInstance().unregister(uri, this.listener);
        
        // use new
        this.listener = listener;
        Sessions.getInstance().register(uri, listener);
        return this;
    }
    
    public RtspSession withState(State newState) {
        Sessions sessions = Sessions.getInstance();
        switch (newState) {
            case ANNOUNCING:
                this.receiveFromClient = true;
                break;
            case DESCRIBING:
                this.receiveFromClient = false;
                this.sdp = sessions.getSdp(uri);
                break;
            case RECORD:
            case PLAY:
                if (isReceiveFromClient()) {
                    sessions.updateSdp(uri, getSdp());
                }
                break;
            case TEARDOWN:
                sessions.unregister(uri, listener);
                
                if (isReceiveFromClient()) {
                    sessions.removeSdp(uri, getSdp());
                }
                break;
            default:
                break;
        }
        this.currentState = newState;
        
        return this;
    }
    

    public void dispatch(InterleavedFrame msg) {
        if (isReceiveFromClient()) {
            Sessions.getInstance().dispatch(uri, msg);
        }
    }
    
    public String getUri() {
        return uri;
    }
    
    public State getState() {
        return currentState;
    }
    
    public String getSdp() {
        return sdp;
    }
    
    
    private boolean isReceiveFromClient() {
        return receiveFromClient;
    }
    
    public String getId() {
        return id;
    }


    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append("{RtspSession#").append(id);
        buf.append(", uri=").append(uri);
        buf.append(receiveFromClient ? ", receive" : ", play");
        
        buf.append("}");
        return buf.toString();
    }

}
