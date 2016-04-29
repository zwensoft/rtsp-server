package com.sengled.cloud.mediaserver.rtsp.rtp;

import jlibrtp.AbstractRTCPSession;

/**
 * RTCP sessions
 * @author 陈修恒
 * @date 2016年4月28日
 */
public class InterLeavedRTCPSession extends AbstractRTCPSession{
    private int rtcpChannel;
    
    public InterLeavedRTCPSession(int rtcpChannel) {
        this.rtcpChannel = rtcpChannel;
    }

    public int rtcpChannel() {
        return rtcpChannel;
    }

    @Override
    public void sendByes() {
        
    }

    @Override
    public void start() {
        
    }
    
    @Override
    public void wakeSenderThread(long ssrc) {
        
    }
}
