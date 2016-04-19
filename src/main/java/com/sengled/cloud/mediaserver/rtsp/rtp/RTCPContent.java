package com.sengled.cloud.mediaserver.rtsp.rtp;

import io.netty.buffer.ByteBuf;

import com.sengled.cloud.mediaserver.rtsp.codec.DefaultInterleavedFrame;

public class RTCPContent  extends DefaultInterleavedFrame {
    public RTCPContent(int channel, ByteBuf payload) {
        super(channel, payload);
    }
    
    @Override
    public RTCPContent copy() {
        RTCPContent rtcp = new RTCPContent(getChannel(), content().copy());
        return rtcp;
    }
    
    @Override
    public RTCPContent duplicate() {
        RTCPContent rtcp = new RTCPContent(getChannel(), content().duplicate());
        return rtcp;
    }
    
    @Override
    public RTCPContent retain() {
        content().retain();
        return this;
    }

    @Override
    public RTCPContent retain(int increment) {
        content().retain(increment);
        return this;
    }
    
    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append("{RTCP, stream.channel=").append(getChannel());
        buf.append(", refCnt=").append(refCnt());
        buf.append("}");
        return buf.toString();
    }
}
