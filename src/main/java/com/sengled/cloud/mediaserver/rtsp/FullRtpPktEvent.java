package com.sengled.cloud.mediaserver.rtsp;

import io.netty.util.ReferenceCountUtil;

public class FullRtpPktEvent extends AbstractRTPEvent<FullRtpPkt> {
    
    public FullRtpPktEvent(int streamIndex, FullRtpPkt packet) {
        super(streamIndex, packet);
    }
    
    @Override
    protected void doDestroy() {
        ReferenceCountUtil.release(source);
        source = null;
        
    }
}
