package com.sengled.cloud.mediaserver.rtsp.interleaved;

import io.netty.buffer.ByteBuf;

import com.sengled.cloud.mediaserver.rtsp.InterLeaved;


/**
 * @author 陈修恒
 * @date 2016年5月10日
 */
public class RtcpContent extends InterleavedFrame implements InterLeaved {
    public RtcpContent(int channel, ByteBuf payload) {
        super(channel, payload);
    }
    
    @Override
    public RtcpContent retain() {
        content().retain();
        return this;
    }
    
       @Override
    public RtcpContent retain(int increment) {
           content().retain(increment);
           return this;
    }
       
       @Override
    public RtcpContent duplicate() {
           return new RtcpContent(channel(), content().duplicate());
    }
    
    public byte[] readBytes() {
        ByteBuf content = content();

        int readerIndex = content.readerIndex();
        byte[] dst = new byte[content.readableBytes()];
        content.readBytes(dst);
        content.readerIndex(readerIndex);
        
        return dst;
    }
    
    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append("{RtcpContent, channel=").append(channel());
        buf.append(", refCnt=").append(content().refCnt());
        buf.append("}");
        return buf.toString();
    }
}
