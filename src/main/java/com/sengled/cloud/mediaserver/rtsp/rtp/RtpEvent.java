package com.sengled.cloud.mediaserver.rtsp.rtp;

import io.netty.buffer.DefaultByteBufHolder;

import javax.sdp.MediaDescription;

import com.sengled.cloud.mediaserver.event.Event;

public class RtpEvent extends DefaultByteBufHolder implements Event {
    private int streamIndex;
    private MediaDescription mediaDescription;
    private RTPContent rtp;
    
    public RtpEvent(int mediaIndex, MediaDescription mediaDescription, RTPContent rtp) {
        super(rtp.content());
        this.streamIndex = mediaIndex;
        this.mediaDescription = mediaDescription;
        this.rtp = rtp;
    }

    public MediaDescription getMediaDescription() {
        return mediaDescription;
    }

    public int getStreamIndex() {
        return streamIndex;
    }

    @Override
    public RtpEvent copy() {
        return new RtpEvent(streamIndex, mediaDescription, rtp.copy());
    }

    @Override
    public RtpEvent duplicate() {
        return new RtpEvent(streamIndex, mediaDescription, rtp.duplicate());
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
