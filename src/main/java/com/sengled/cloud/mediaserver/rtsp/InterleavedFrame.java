package com.sengled.cloud.mediaserver.rtsp;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.DefaultByteBufHolder;

/**
 * Rtsp Interleaved Frame
 * <p>
 * RTP Over TCP
 * 
 * @author 陈修恒
 * @date 2016年4月29日
 */
public class InterleavedFrame extends DefaultByteBufHolder implements InterLeaved  {
    private int channel;
    
    public InterleavedFrame(int channel, ByteBuf payload) {
        super(payload);
        this.channel = channel;
    }
    
    protected long getUnsignedInt(int offset) {
        try {
            ByteBuf content = content();
            return content.getUnsignedInt(content.readerIndex() + offset);
        } catch(Exception e){
            throw new IllegalArgumentException(e);
        }
    }
    
    protected void setUnsignedInt(int offset, long value) {
        ByteBuf content = content();
        content.setInt(content.readerIndex() + offset, (int)(value & 0xFFFFFFFFL));
    }
    
    
    protected int getUnsignedMedium(int offset) {
        ByteBuf content = content();
        return content.getUnsignedMedium(content.readerIndex() + offset);
    }
    
    protected int getUnsignedShort(int offset) {
        ByteBuf content = content();
        return content.getUnsignedShort(content.readerIndex() + offset);
    }
    
    protected short getUnsignedByte(int offset) {
        ByteBuf content = content();
        return content.getUnsignedByte(content.readerIndex() + offset);
    }
    
    
    /* (non-Javadoc)
     * @see com.sengled.cloud.mediaserver.rtsp.codec.IInterLeavedFrame#getChannel()
     */
    @Override
    public int channel() {
        return channel;
    }

    /* (non-Javadoc)
     * @see com.sengled.cloud.mediaserver.rtsp.codec.IInterLeavedFrame#content()
     */
    @Override
    public ByteBuf content() {
        return super.content();
    }
    
    @Override
    public InterleavedFrame copy() {
        return new InterleavedFrame(channel, content().copy());
    }
    
    /* (non-Javadoc)
     * @see com.sengled.cloud.mediaserver.codec.rtsp.InterleavedFrame#duplicate()
     */
    @Override
    public InterleavedFrame duplicate() {
        return new InterleavedFrame(channel, content().duplicate());
    }
    
    /* (non-Javadoc)
     * @see com.sengled.cloud.mediaserver.codec.rtsp.InterleavedFrame#retain()
     */
    @Override
    public InterleavedFrame retain() {
        content().retain();
        return this;
    }
    
    /* (non-Javadoc)
     * @see com.sengled.cloud.mediaserver.codec.rtsp.InterleavedFrame#retain(int)
     */
    @Override
    public InterleavedFrame retain(int increment) {
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

