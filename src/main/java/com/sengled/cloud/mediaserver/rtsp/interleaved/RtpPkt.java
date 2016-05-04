package com.sengled.cloud.mediaserver.rtsp.interleaved;

import jlibrtp.IRtpPkt;
import io.netty.buffer.ByteBuf;

/**
 * rtp 包
 * 
 * @author 陈修恒
 * @date 2016年4月29日
 */
public class RtpPkt extends InterleavedFrame implements IRtpPkt {
    private int headerLength;
    
    public RtpPkt(int channel, ByteBuf payload) {
        super(channel, payload);
        
        headerLength = 12; 
        
        boolean hasExtHeader = (getFlags() & 0x10) > 0;
        int numCC = getFlags() & 0x0F;
        headerLength += numCC * 4;
        
        if (hasExtHeader) {
            int extLength = getUnsignedShort(headerLength + 2);
            headerLength += 4; // defined by profile + length
            headerLength += extLength; // length
        }
    }
    
    
    
    @Override
    public RtpPkt duplicate() {
        return new RtpPkt(channel(), content().duplicate());
    }
    
    
    @Override
    public RtpPkt retain() {
        content().retain();
        return this;
    }
    
    
    @Override
    public RtpPkt retain(int increment) {
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
    

    public int getSeqNumber() {
        return getUnsignedShort(2);
    }
    
    public void setSeqNumber(int seq) {
        setUnsignedShort(2, seq);
    }
   
 

    public long getTimestamp() {
        return getUnsignedInt(4);
    }
    
    
    public void setTimestamp(long timestamp) {
        setUnsignedInt(4, timestamp);
    }
    
    public long ssrc() {
        return getUnsignedInt(8);
    }
    
    public void ssrc(long ssrc) {
        setUnsignedInt(8, ssrc);
    }
    
    public ByteBuf data() {
        ByteBuf content = content();
		int contentLength = content.readableBytes();
        return content.slice(content.readerIndex() + headerLength(), contentLength - headerLength());
    }

    public int contentLength() {
        return  content().readableBytes();
    }
    
    public int dataLength() {
        return  content().readableBytes() - headerLength();
    }
    
    
    public int headerLength() {
        return headerLength;
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append("{RTP");
        buf.append(", refCnt=").append(refCnt());
        buf.append(", channel=").append(channel());
        buf.append(", pType=").append(getPayloadType());
        buf.append(", seq=").append(getSeqNumber());
        buf.append(", t=").append(getTimestamp());
        buf.append(", sc=0x").append(Long.toHexString(ssrc()));
        buf.append(", size=").append(content().readableBytes());
        buf.append(isMarker() ? " Marker" : "");
        buf.append("}");
        return buf.toString();
    }

}
