package com.sengled.cloud.mediaserver.rtsp.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

public class RtspInterleavedFrameEncoder extends MessageToByteEncoder<InterleavedFrame> {
    @Override
    public boolean acceptOutboundMessage(Object msg) throws Exception {
        return msg instanceof InterleavedFrame;
    }
    
    @Override
    protected void encode(ChannelHandlerContext ctx,
                          InterleavedFrame msg,
                          ByteBuf out) throws Exception {
        out.writeByte('$');
        out.writeByte(msg.getChannel());
        out.writeShort(msg.content().readableBytes());
        out.writeBytes(msg.content());
    }
}
