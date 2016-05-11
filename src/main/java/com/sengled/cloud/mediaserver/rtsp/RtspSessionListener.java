package com.sengled.cloud.mediaserver.rtsp;

import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sengled.cloud.mediaserver.rtsp.event.NtpTimeEvent;
import com.sengled.cloud.mediaserver.rtsp.event.RtpPktEvent;
import com.sengled.cloud.mediaserver.rtsp.event.TearDownEvent;
import com.sengled.cloud.mediaserver.rtsp.interleaved.RtcpContent;
import com.sengled.cloud.mediaserver.rtsp.interleaved.RtpPkt;
import com.sengled.cloud.mediaserver.rtsp.rtp.InterLeavedRTPSession;

public class RtspSessionListener implements GenericFutureListener<Future<? super Void>> {
    private static final Logger logger = LoggerFactory.getLogger(RtspSessionListener.class);
    
    final private RtspSession session;
    final private AtomicLong sendBufferSize = new AtomicLong();
    final private int maxRtpBufferSize;
    final private int minRtpBufferSize;

    public RtspSessionListener(RtspSession mySession, int maxRtpBufferSize) {
        super();
        this.session = mySession;
        this.maxRtpBufferSize = maxRtpBufferSize;
        this.minRtpBufferSize = 1 + maxRtpBufferSize / 2;
    }


    
    /**
     * 初始化
     * 
     * @param producer 音视频数据源
     */
    public void init(RtspSession producer) {
        InterLeavedRTPSession[] srcSessions = producer.getRTPSessions();
        InterLeavedRTPSession[] dstSessions =  session.getRTPSessions();
        
        for (int i = 0; i < srcSessions.length; i++) {
            InterLeavedRTPSession src = srcSessions[i];
            InterLeavedRTPSession dst = dstSessions[i];
            if (null == src || null == dst) {
                continue;
            }
            
            dst.setNtpTime(src.getNtpTime());
        }
    }
    
    public void fireExceptionCaught(Exception ex) {
        session.channelHandlerContext().fireExceptionCaught(ex);
    }
    
    public <T> void on(RtpEvent<T> event) {
        if (session.isDestroyed()) {
        	return; // session has been release
        }

        if(event instanceof RtpPktEvent) {
            onRtpPktEvent(((RtpPktEvent)event));
        } else if (event instanceof NtpTimeEvent) {
            // 同步视频时间
            onNtpTimeEvent((NtpTimeEvent)event);
            
        }else if (event instanceof TearDownEvent) {
            session.destroy("publisher has teardown");
        } else {
            logger.warn("ignore {}", event);
        }
    }


    public void receiveRtcpEvent(RtcpContent event) {
        try {
            /**
            int streamIndex = session.getStreamIndex(event);
            InterLeavedRTPSession rtpSession = session.getRTPSessions()[streamIndex];
            if (null != rtpSession) {
                List<RtcpPkt> pkts = RTCPCodec.decode(rtpSession, event.content(), event.content().length);
                for (RtcpPkt pkt : pkts) {
                    logger.debug("stream#{} receive {}", streamIndex, pkt);
                }
            }
            */
        } finally {
            ReferenceCountUtil.release(event);
        }

    }

    /**
     * 同步 ntp 时间. 以发送端的时间为准
     * @param event
     */
    private void onNtpTimeEvent(NtpTimeEvent event) {
        InterLeavedRTPSession rtp = session.getRTPSessions()[event.getStreamIndex()];
        if (null != rtp) {
        	rtp.setNtpTime(event.getSource());
        }
    }

    private void onRtpPktEvent(RtpPktEvent rtpEvent) {
        int streamIndex = rtpEvent.getStreamIndex();
        if (!session.isStreamSetup(streamIndex)) {
            logger.debug("stream#{} NOT setup", streamIndex);
        }
    
        RtpPkt rtpObj = rtpEvent.getSource();
        InterLeavedRTPSession[] rtpSessions = session.getRTPSessions();
        InterLeavedRTPSession rtpSession = rtpSessions[streamIndex];
        
        switch (session.state()) {
            case PLAYING:
                if (sendBufferSize.get() < maxRtpBufferSize || !rtpObj.isFrameStart()) {
                    boolean sent = rtpSession.sendRtpPkt(rtpObj, this);
                    if (sent) {
                        sendBufferSize.incrementAndGet();
                    }
                } else {
                    session.state(PlayState.WAITING);
                }
                break;
            case WAITING:
                if (sendBufferSize.get() > minRtpBufferSize) {
                    return;
                } else {
                    session.state(PlayState.PLAYING);
                    
                    if (!rtpObj.isFrameStart()) {
                        return;
                    } else {
                        boolean sent = rtpSession.sendRtpPkt(rtpObj, this);
                        if (sent) {
                            sendBufferSize.incrementAndGet();
                        }
                    }
                }
                break;
            case END:
                break;
            default:
                break;
        }
     
    }


    /**
     * 监听消息是否成功发送出去了
     */
    @Override
    public void operationComplete(Future<? super Void> future) throws Exception {
        sendBufferSize.decrementAndGet();
    }

    
    
    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append("{RtpSessionDispatcher");
        buf.append(", name = ").append(session.getName());
        buf.append(", ").append(session.channel().remoteAddress());
        buf.append("}");
        return buf.toString();
    }
}
