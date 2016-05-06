package com.sengled.cloud.mediaserver.rtsp.event;

import io.netty.util.ReferenceCountUtil;

import com.sengled.cloud.mediaserver.rtsp.interleaved.RtpPkt;

public class RtpPktEvent  extends AbstractRTPEvent<RtpPkt>{

    public RtpPktEvent(int streamIndex, RtpPkt source) {
        super(streamIndex, source);
    }

    @Override
    protected void doDestroy() {
        ReferenceCountUtil.release(source);
        source = null;
    }
}
