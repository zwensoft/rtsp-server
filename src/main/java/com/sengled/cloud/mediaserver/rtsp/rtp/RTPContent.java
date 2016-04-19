package com.sengled.cloud.mediaserver.rtsp.rtp;

import io.netty.buffer.ByteBuf;

import com.sengled.cloud.mediaserver.rtsp.codec.DefaultInterleavedFrame;
import com.sengled.cloud.mediaserver.rtsp.codec.InterleavedFrame;

public class RTPContent extends DefaultInterleavedFrame {

    public static RTPContent wrap(InterleavedFrame frame) {
        return new RTPContent(frame.getChannel(), frame.content());
    }
    
    public static RTPContent wrap(int channel, ByteBuf content) {
        return new RTPContent(channel, content);
    }

    public RTPContent(int channel, ByteBuf payload) {
        super(channel, payload);
    }
    
    @Override
    public RTPContent copy() {
        return new RTPContent(getChannel(), content().copy());
    }
    
    @Override
    public RTPContent duplicate() {
        return new RTPContent(getChannel(), content().duplicate());
    }
    
    @Override
    public RTPContent retain() {
        content().retain();
        return this;
    }
    
    
    @Override
    public RTPContent retain(int increment) {
        content().retain(increment);
        return this;
    }

    public int getFlags() {
        return getUnsignedByte(0);
    }
    
    public boolean isMarker() {
        return (getUnsignedByte(1) & 0x80) > 0;
    }
    
    public int getPayloadType() {
        return (getUnsignedByte(1) & 0x7F);
    }
    
    public int getSeqNo() {
        return getUnsignedShort(2);
    }
    
    public long getTimestamp() {
        return getUnsignedInt(4);
    }
    
    
    public long setTimestamp(long timestamp) {
        return getUnsignedInt(4);
    }
    
    public long getSC() {
        return getUnsignedInt(8);
    }
    
    public ByteBuf payload() {
        int headerLength = 12;
        int contentLength = content().readableBytes();
        return content().slice(headerLength, contentLength - headerLength);
    }
    
    
    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append("{RTP");
        buf.append(", pType=").append(getPayloadType());
        buf.append(", seq=").append(getSeqNo());
        buf.append(", t=").append(getTimestamp());
        buf.append(", sc=0x").append(Long.toHexString(getSC()));
        buf.append(", size=").append(content().readableBytes());
        buf.append(isMarker() ? " Marker" : "");
        buf.append("}");
        return buf.toString();
    }
    
}
