package com.sengled.cloud.mediaserver.rtsp;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import jlibrtp.RtcpPkt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sengled.cloud.mediaserver.rtsp.event.NtpTimeEvent;
import com.sengled.cloud.mediaserver.rtsp.event.RtpPktEvent;
import com.sengled.cloud.mediaserver.rtsp.event.TearDownEvent;
import com.sengled.cloud.mediaserver.rtsp.interleaved.RtcpContent;
import com.sengled.cloud.mediaserver.rtsp.interleaved.RtpPkt;
import com.sengled.cloud.mediaserver.rtsp.rtp.InterLeavedRTPSession;
import com.sengled.cloud.mediaserver.rtsp.rtp.RTCPCodec;

public class RtspSessionListener implements GenericFutureListener<Future<? super Void>> {
    private static final Logger logger = LoggerFactory.getLogger(RtspSessionListener.class);
    
    final private RtspSession session;
    final private AtomicLong bufferSize = new AtomicLong();
    final private int maxRtpBufferSize;
    

    public RtspSessionListener(RtspSession mySession, int maxRtpBufferSize) {
        super();
        this.session = mySession;
        this.maxRtpBufferSize = maxRtpBufferSize;
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
    	int streamIndex = session.getStreamIndex(event);
    	InterLeavedRTPSession rtpSession = session.getRTPSessions()[streamIndex];
    	if (null != rtpSession) {
    		List<RtcpPkt> pkts = RTCPCodec.decode(rtpSession, event.content(), event.content().length);
    		for (RtcpPkt pkt : pkts) {
				logger.debug("stream#{} receive {}", streamIndex, pkt);
			}
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
        
        // send or not ?
        if (bufferSize.get() < maxRtpBufferSize) {
            bufferSize.incrementAndGet();
            rtpSession.sendRtpPkt(rtpObj.duplicate(), this); // 拷贝一份，重复使用
        } else {
            // reset rtp sessions
            for (int i = 0; i < rtpSessions.length; i++) {
                InterLeavedRTPSession subSession = rtpSessions[i];
                if (null != subSession) {
                    subSession.reset();
                }
            }
        }
    }


    /**
     * 监听消息是否成功发送出去了
     */
    @Override
    public void operationComplete(Future<? super Void> future) throws Exception {
        bufferSize.decrementAndGet();
    }


}
