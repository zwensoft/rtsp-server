package com.sengled.cloud.mediaserver.rtsp.event;

import com.sengled.cloud.mediaserver.rtsp.RtspSession;

/**
 * Rtsp Session 被销毁
 * 
 * @author 陈修恒
 * @date 2016年5月3日
 */
public class RtspSessionRemovedEvent extends RtspSessionEvent {

    public RtspSessionRemovedEvent(RtspSession session) {
        super(session);
    }

}
