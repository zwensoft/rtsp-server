package com.sengled.cloud.mediaserver.rtsp.rtp;

import com.sengled.cloud.mediaserver.rtsp.InterLeaved;

public class RtcpContent implements InterLeaved {
    private int channel;
    private byte[] bytes;
    public RtcpContent(int channel, byte[] bytes) {
        super();
        this.channel = channel;
        this.bytes = bytes;
    }
    
    public byte[] content() {
        return bytes;
    }
    
    
    public int channel() {
        return channel;
    }

    public int length() {
        return null != bytes ? bytes.length : 0;
    }
}
