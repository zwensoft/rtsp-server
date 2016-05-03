package com.sengled.cloud.mediaserver.rtsp.event;

import com.sengled.cloud.mediaserver.rtsp.RtspSession;

/**
 * rtsp session 事务
 * @author 陈修恒
 * @date 2016年5月3日
 */
public class RtspSessionEvent {
    public RtspSession session;

    public RtspSessionEvent(RtspSession session) {
        super();
        this.session = session;
    }

    public RtspSession getSession() {
        return session;
    }
}
