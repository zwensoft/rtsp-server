package com.sengled.cloud.mediaserver.rtsp.rtp;

import javax.sdp.MediaDescription;

import com.sengled.cloud.mediaserver.event.Dispatcher;
import com.sengled.cloud.mediaserver.rtsp.Sessions;
import com.sengled.cloud.mediaserver.rtsp.codec.InterleavedFrame;

public class RTPStream extends Dispatcher {
    private int rtpChannel;
    private int rtcpChannel;
    private MediaDescription md;

    public RTPStream(MediaDescription md, int rtpChannel, int rtcpChannel) {
        this.md = md;
        this.rtcpChannel = rtcpChannel;
        this.rtpChannel = rtpChannel;
    }


    public MediaDescription getMediaDescription() {
        return md;
    }

    public int getRtcpChannel() {
        return rtcpChannel;
    }

    public int getRtpChannel() {
        return rtpChannel;
    }


    public void dispatch(String uri, int streamIndex, InterleavedFrame msg) {
        Sessions.getInstance().dispatch(uri, new RtpEvent(streamIndex, md, msg.content()));
    }

}
