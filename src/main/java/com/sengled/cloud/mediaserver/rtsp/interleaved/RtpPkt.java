package com.sengled.cloud.mediaserver.rtsp.interleaved;

import jlibrtp.IRtpPkt;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/**
 * rtp 包
 * 
 * @author 陈修恒
 * @date 2016年4月29日
 */
public class RtpPkt extends InterleavedFrame implements IRtpPkt {
    /** rtp 头 12 字节是可以修改的 **/
    private static final int WRITEABLE_LENGTH = 12;
    
    private final int headerLength;
    private boolean isFrameStart = false;
    
    private RtpPkt(int channel, ByteBuf payload, int headerLength, boolean isFrameStart) {
        super(channel, payload);
        this.headerLength = headerLength;
        this.isFrameStart = isFrameStart;
    }
    
    public RtpPkt(int channel, ByteBuf payload) {
        super(channel, Unpooled.unmodifiableBuffer(payload));
        
        int headerLength = WRITEABLE_LENGTH; 
        
        boolean hasExtHeader = (getFlags() & 0x10) > 0;
        int numCC = getFlags() & 0x0F;
        headerLength += numCC * 4;
        
        if (hasExtHeader) {
            int extLength = getUnsignedShort(headerLength + 2);
            headerLength += 4; // defined by profile + length
            headerLength += extLength; // length
        }
        
        this.headerLength = headerLength;
    }

    /**
     * 拷贝一个新的  RtpPkt， rtp 头可以任意修改。
     * 但是负载部分不能改
     * 
     * 调用  share 以后， 一定要调用 release 进行释放
     * @return
     */
    public RtpPkt share() {
        ByteBuf content = content().duplicate();

        // 拷贝 rtp 头 （不包括扩展头）
        int rtpHeaderLength = WRITEABLE_LENGTH;
        ByteBuf header = content.copy(0, rtpHeaderLength);
        
        // 拷贝 rtp 负载，包括扩展头
        int dataIndex = content.readerIndex() + rtpHeaderLength;
        int dataLength = content.readableBytes() - rtpHeaderLength;
        ByteBuf dataAfterSimpleHead = content.slice(dataIndex, dataLength).retain();

        
        // 组建成新的  rtp 包
        ByteBuf newPayload = Unpooled.wrappedBuffer(header, dataAfterSimpleHead);
        return new RtpPkt(channel(), newPayload, headerLength(), isFrameStart());
    }
    
    @Override
    public RtpPkt duplicate() {
        return new RtpPkt(channel(), content().duplicate(), headerLength(), isFrameStart());
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
    
    public boolean isFrameStart() {
        return isFrameStart;
    }

    public void setFrameStart(boolean isFrameStart) {
        this.isFrameStart = isFrameStart;
    }
    
    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append("{").append(getClass().getSimpleName());
        buf.append(", ssrc=").append(ssrc());
        buf.append(", start=").append(isFrameStart());
        buf.append(", channel=").append(channel());
        buf.append(", refCnt=").append(refCnt());
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
