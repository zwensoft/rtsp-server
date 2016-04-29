package com.sengled.cloud.mediaserver.rtsp.rtp;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.util.ReferenceCountUtil;
import jlibrtp.RtcpPktSR;

import com.sengled.cloud.mediaserver.rtsp.interleaved.FullRtpPkt;

/**
 * 发送  rtcp 包
 * 
 * @author 陈修恒
 * @date 2016年4月28日
 */
public class InterLeavedRTCPSender {
    private long lastSentSRTime = 0;
    
    private InterLeavedRTPSession rtpSession;
    private InterLeavedRTCPSession rtcpSession;
    
    
    public InterLeavedRTCPSender(InterLeavedRTPSession rtpSession,
            InterLeavedRTCPSession rtcpSession) {
        super();
        this.rtpSession = rtpSession;
        this.rtcpSession = rtcpSession;
    }
    
    
    public void sendRtcpIfNeed(FullRtpPkt packet, Channel out) {
        try {
            long now = System.currentTimeMillis();
            if (now - lastSentSRTime > 5 * 1000) { // 控制  5s 发送一个 RtcpSR 包
                doSendRtcp(packet, out);
    
                lastSentSRTime = now;
            }
        } finally {
            ReferenceCountUtil.release(packet);
        }
    }


    private void doSendRtcp(FullRtpPkt packet,
                            Channel out) {
        RtcpPktSR sr = new RtcpPktSR(rtpSession.ssrc(), rtpSession.sentPktCount, rtpSession.sentOctetCount, null);
        sr.rtpTs = packet.getTimestamp();
        
        sr.encode();
        
        byte[] bytes = sr.rawPkt;
        ByteBuf payload = out.alloc().buffer(4 + bytes.length);
        payload.writeByte('$');
        payload.writeByte(rtcpChannel());
        payload.writeShort(bytes.length);
        payload.writeBytes(bytes);
        out.writeAndFlush(payload);
    }


    private int rtcpChannel() {
        return rtcpSession.rtcpChannel();
    }
}
