package com.sengled.cloud.mediaserver.rtsp;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import jlibrtp.Participant;
import jlibrtp.RtcpPkt;

import org.apache.commons.lang.time.DateFormatUtils;
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
    final private int maxBufferSize;
    final private AtomicLong bufferSize = new AtomicLong();
    

    private PlayState state = PlayState.INITED;
    
    public RtspSessionListener(RtspSession mySession, int maxRtpBufferSize) {
        super();
        this.session = mySession;
        this.maxBufferSize = maxRtpBufferSize;
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
				logger.info("stream#{} receive {}", streamIndex, pkt);
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



    protected void sendRtcpPktSR(int streamIndex, InterLeavedRTPSession dst) {
        if (bufferIfFull()) {
            return;
        }

        Participant p = dst.findParticipant();
        if (p.lastNtpTs1 < 0) {
            return;
        } 
        
        
        // TODO
        // 丢弃的 SR
        /**
        
        RtcpPktSR sr = new RtcpPktSR(dst.ssrc(), dst.sentPktCount, dst.sentOctetCount, null);
        sr.ntpTs1 = p.lastNtpTs1;
        sr.ntpTs2 = p.lastNtpTs2;
        sr.rtpTs = p.lastRtpPkt;
        
        sr.encode();
        byte[] rawByte = sr.rawPkt;
        int rtcpChannel = dst.rtcpChannel();

        ByteBufAllocator alloc = channel().alloc();
        ByteBuf payload = alloc.buffer(4 + rawByte.length);
        payload.writeByte('$');
        payload.writeByte(rtcpChannel);
        payload.writeShort(rawByte.length);
        payload.writeBytes(rawByte);
        channel().writeAndFlush(payload);
        */
    }
    
    private void onRtpPktEvent(RtpPktEvent rtpEvent) {
        int streamIndex = rtpEvent.getStreamIndex();
        if (!session.isStreamSetup(streamIndex)) {
            logger.debug("stream#{} NOT setup", streamIndex);
        }
    
        boolean sent = false;
        boolean isNewFrame = false;
        RtpPkt rtpObj = rtpEvent.getSource();
        InterLeavedRTPSession rtpSession = session.getRTPSessions()[streamIndex];

        if (rtpObj.getTimestamp() == rtpSession.getPlayingTimestamp()) {
            sent = true;
        } else { // new frame
            switch (state) {
                case INITED:
                    if (isSuitableForPlaying(rtpObj)) {
                        state = PlayState.PLAYING;
                    }
                    break;
                case PLAYING:
                    if (bufferIfFull()) {
                        state = PlayState.BUFFER_FULL;
                    }
                    break;
                case BUFFER_FULL:
                    if (bufferSize.get() < 1 && isSuitableForPlaying(rtpObj)) {
                        state = PlayState.PLAYING;
                    } else if (bufferSize.get() < 1) {
                        state = PlayState.INITED;
                    }
                default:
                    throw new IllegalStateException("illegal PlayState[" + state + "]");
            }
            
            sent = (state == PlayState.PLAYING);
            isNewFrame = true;
        }
        
        // send or not ?
        if(!sent) {
            logger.debug("stream#{} drop: {}.", streamIndex, rtpObj);
        } else {
        	bufferSize.incrementAndGet();
            rtpSession.sendRtpPkt(isNewFrame, rtpObj.duplicate(), this); // 拷贝一份，重复使用
            
            if (isNewFrame && logger.isDebugEnabled()) {
                MediaStream mediaStream = session.getRTPSessions()[streamIndex].getMediaStream();
                long playingTimeMillis = rtpSession.getPlayingTimeMillis();
                String logTime = DateFormatUtils.format(playingTimeMillis, "yyyy-MM-dd HH:mm:ss.SSS");
                logger.debug("stream#{} {}, {}. {} bytes, seq = {}", streamIndex, logTime, mediaStream.getCodec(), rtpObj.dataLength(), rtpObj.getSeqNumber());
            }
        }
    }

    /**
     * 可以作为媒体流的第一帧开始播放
     * 
     * @param fullRtp
     * @return
     */
    private boolean isSuitableForPlaying(RtpPkt fullRtp) {
        boolean onlyVideo = !session.hasVideo(); // 在对讲模式下， 只有音频，没有视频
        return onlyVideo || fullRtp.isKeyFrame();
        //return true;
    }
    
    
    private boolean bufferIfFull() {
        return maxBufferSize > 0 && bufferSize.get() > maxBufferSize;
    }


    /**
     * 监听消息是否成功发送出去了
     */
    @Override
    public void operationComplete(Future<? super Void> future) throws Exception {
        bufferSize.decrementAndGet();
    }


}
