package com.sengled.cloud.mediaserver.rtsp.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.DefaultByteBufHolder;

public class DefaultInterleavedFrame extends DefaultByteBufHolder implements InterleavedFrame  {
    private int channel;
    
    public DefaultInterleavedFrame(int channel, ByteBuf payload) {
        super(payload);
        this.channel = channel;
    }
    
    public long getUnsignedInt(int offset) {
        ByteBuf content = content();
        return content.getUnsignedInt(content.readerIndex() + offset);
    }
    
    public int getUnsignedMedium(int offset) {
        ByteBuf content = content();
        return content.getUnsignedMedium(content.readerIndex() + offset);
    }
    
    public int getUnsignedShort(int offset) {
        ByteBuf content = content();
        return content.getUnsignedShort(content.readerIndex() + offset);
    }
    
    public short getUnsignedByte(int offset) {
        ByteBuf content = content();
        return content.getUnsignedByte(content.readerIndex() + offset);
    }
    
    
    
    /* (non-Javadoc)
     * @see com.sengled.cloud.mediaserver.codec.rtsp.InterleavedFrame#getChannel()
     */
    @Override
    public int getChannel() {
        return channel;
    }

    /* (non-Javadoc)
     * @see com.sengled.cloud.mediaserver.codec.rtsp.InterleavedFrame#copy()
     */
    @Override
    public DefaultInterleavedFrame copy() {
        return new DefaultInterleavedFrame(channel, content().copy());
    }
    
    /* (non-Javadoc)
     * @see com.sengled.cloud.mediaserver.codec.rtsp.InterleavedFrame#duplicate()
     */
    @Override
    public DefaultInterleavedFrame duplicate() {
        return new DefaultInterleavedFrame(channel, content().duplicate());
    }
    
    /* (non-Javadoc)
     * @see com.sengled.cloud.mediaserver.codec.rtsp.InterleavedFrame#retain()
     */
    @Override
    public DefaultInterleavedFrame retain() {
        content().retain();
        return this;
    }
    
    /* (non-Javadoc)
     * @see com.sengled.cloud.mediaserver.codec.rtsp.InterleavedFrame#retain(int)
     */
    @Override
    public DefaultInterleavedFrame retain(int increment) {
        content().retain(increment);
        return this;
    }
    
    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append("{Rtsp Interleaved Frame, channel=").append(channel);
        buf.append(", bytes=").append(content().readableBytes());
        buf.append("}");
        return buf.toString();
    }
    
}

