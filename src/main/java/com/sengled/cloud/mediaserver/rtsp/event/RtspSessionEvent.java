package com.sengled.cloud.mediaserver.rtsp.event;

import com.sengled.cloud.mediaserver.rtsp.RtspSession;

/**
 * rtsp session 事件
 * 
 * @see RtspSessionRemovedEvent
 * @see RtspSessionRemovedEvent
 * 
 * @author 陈修恒
 * @date 2016年5月3日
 */
public class RtspSessionEvent {
    private int numSessions;
    private RtspSession session;

    public RtspSessionEvent(int numSessions, RtspSession session) {
        super();
        this.numSessions = numSessions;
        this.session = session;
    }

    public RtspSession getSession() {
        return session;
    }
    
    public int getNumSessions() {
        return numSessions;
    }
}
