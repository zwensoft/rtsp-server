package com.sengled.cloud.mediaserver.rtsp;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

import java.util.concurrent.atomic.AtomicLong;

import jlibrtp.AbstractParticipant;
import jlibrtp.tcp.InterLeavedParticipant;
import jlibrtp.tcp.InterLeavedRTPSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sengled.cloud.mediaserver.rtsp.rtp.RtcpContent;
import com.sengled.cloud.mediaserver.rtsp.rtp.RtpPkt;

public class RtspSessionListener implements GenericFutureListener<Future<? super Void>> {
    private static final Logger logger = LoggerFactory.getLogger(RtspSessionListener.class);
    
    final private RtspSession rtsp;
    final private int maxBufferSize;
    final private AtomicLong bufferSize = new AtomicLong();
    
    public RtspSessionListener(RtspSession mySession, int maxRtpBufferSize) {
        super();
        this.rtsp = mySession;
        this.maxBufferSize = maxRtpBufferSize;
    }

    private Channel channel() {
        return rtsp.channel();
    }

    public void fireExceptionCaught(Exception ex) {
        rtsp.channelHandlerContext().fireExceptionCaught(ex);
    }
    
    public <T> void on(RtpEvent<T> event) {
        InterLeavedRTPSession rtp = rtsp.getRTPSessions()[event.getStreamIndex()];
        if (null == rtp) {
            return;
        }
        
        if(event instanceof FullRtpPktEvent) {
            onFullRtpPktEvent(rtp, ((FullRtpPktEvent)event));
        } else if (event instanceof NtpTimeEvent) {
            // 同步视频时间
            syncNtpTime(event.getStreamIndex(), rtp, (NtpTimeEvent)event);
            
        }else if (event instanceof TearDownEvent) {
            TearDownEvent tearDown = (TearDownEvent)event;
            
        } else {
            logger.warn("ignore {}", event);
        }
    }


    public void onRtcpEvent(RtcpContent event) {
        logger.debug("ignore rtcp event, {}", event);
    }

    /**
     * 同步 ntp 时间. 以发送端的时间为准
     * 
     * @param rtp
     * @param event
     */
    private void syncNtpTime(int streamIndex, 
                             InterLeavedRTPSession rtp,
                             NtpTimeEvent event) {
        AbstractParticipant p = findParticipant(rtp);
        NtpTime ntp = event.getSource();
        
        p.lastRtpPkt = ntp.getRtpTime();
        p.lastNtpTs1 = ntp.getNtpTs1();
        p.lastNtpTs2 = ntp.getNtpTs2();
        logger.info("stream#{}, {}", streamIndex, ntp);
    }


    /**
     * Find out whether a participant with this SSRC is known.
     * 
     * If the user is unknown, and the system is operating in unicast mode,
     * try to match the ip-address of the sender to the ip address of a
     * previously unmatched target
     * 
     * @param rtpSession 
     * @return the relevant participant, possibly newly created
     */
    private AbstractParticipant findParticipant(InterLeavedRTPSession rtpSession) {
        AbstractParticipant p = rtpSession.partDb().getParticipant(rtpSession.ssrc());
        if(p == null) {
            p = new InterLeavedParticipant(rtpSession, rtpSession.ssrc());
            rtpSession.partDb().addParticipant(2,p);
        }
        return p;
    }
    
    private void onFullRtpPktEvent(InterLeavedRTPSession rtpSession, FullRtpPktEvent rtpEvent) {
        boolean sent = false;
        int streamIndex = rtpEvent.getStreamIndex();
        FullRtpPkt fullRtp = rtpEvent.getSource();
        
        
        // 如果缓冲区没满，则可以发送
        if (!bufferIfFull(maxBufferSize, bufferSize.get())) {
            sendFullRtpPkt(rtpSession, fullRtp, streamIndex);
            sent = true;
        }
        
        if (!sent) {
            logger.debug("session drop: {}.", fullRtp);
        }
    }

    private void sendFullRtpPkt(InterLeavedRTPSession rtpSess, FullRtpPkt fullRtp, long maxRtpBufferSize) {
        FullRtpPkt packet = fullRtp.duplicate(); // 支持多次读取
        AbstractParticipant p = findParticipant(rtpSess);

        // 统计流量
        rtpSess.sentPktCount ++;
        rtpSess.sentOctetCount += packet.dataLength();
        
        
        
        // 依次发送  rtp 包
        int payloadLength;
        ByteBufAllocator alloc = channel().alloc();
        for (RtpPkt rtpObj : packet.contents()) {
            payloadLength = rtpObj.content().readableBytes();
            
            rtpObj.setSsrc(rtpSess.ssrc());
            
            ByteBuf payload = alloc.buffer(4 + payloadLength);
            payload.writeByte('$');
            payload.writeByte(rtpSess.rtpChannel());
            payload.writeShort(payloadLength);
            payload.writeBytes(rtpObj.content());
            
            bufferSize.getAndIncrement();
            channel().writeAndFlush(payload, channel().newPromise().addListener(this));
        }
    }
    
    
    private boolean bufferIfFull(long maxRtpBufferSize,long bufferSize) {
        return maxRtpBufferSize > 0 && bufferSize > maxRtpBufferSize;
    }


    /**
     * 监听消息是否成功发送出去了
     */
    @Override
    public void operationComplete(Future<? super Void> future) throws Exception {
        bufferSize.decrementAndGet();
    }


}
