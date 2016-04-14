package com.sengled.cloud.mediaserver.rtsp.rtp;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.DefaultByteBufHolder;

import javax.sdp.MediaDescription;

import com.sengled.cloud.mediaserver.event.Event;

public class RtpEvent extends DefaultByteBufHolder implements Event {
    private int streamIndex;
    private MediaDescription mediaDescription;

    public RtpEvent(int mediaIndex, MediaDescription mediaDescription, ByteBuf content) {
        super(content);
        this.streamIndex = mediaIndex;
        this.mediaDescription = mediaDescription;
    }

    public MediaDescription getMediaDescription() {
        return mediaDescription;
    }

    public int getStreamIndex() {
        return streamIndex;
    }

    @Override
    public RtpEvent copy() {
        return new RtpEvent(streamIndex, mediaDescription, content().copy());
    }

    @Override
    public RtpEvent duplicate() {
        return new RtpEvent(streamIndex, mediaDescription, content().duplicate());
    }

    @Override
    public RtpEvent retain() {
        content().retain();
        return this;
    }

    @Override
    public RtpEvent retain(int increment) {
        content().retain(increment);
        return this;
    }

}
