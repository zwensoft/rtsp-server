package com.sengled.cloud.mediaserver.rtsp.event;

import com.sengled.cloud.mediaserver.rtsp.RtspSession;

/**
 * Rtsp Session 被创建
 * 
 * @author 陈修恒
 * @date 2016年5月3日
 */
public class RtspSessionUpdatedEvent extends RtspSessionEvent {

    public RtspSessionUpdatedEvent(RtspSession session) {
        super(session);
    }

}
