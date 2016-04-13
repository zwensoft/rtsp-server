package com.sengled.cloud.mediaserver.rtsp.codec;

import io.netty.buffer.ByteBufHolder;



public interface InterleavedFrame extends ByteBufHolder {

    public abstract int getChannel();

    public abstract InterleavedFrame copy();

    public abstract InterleavedFrame duplicate();

    public abstract InterleavedFrame retain();

    public abstract InterleavedFrame retain(int increment);
}
