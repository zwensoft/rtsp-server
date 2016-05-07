package com.sengled.cloud.mediaserver.rtsp;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

import java.util.concurrent.atomic.AtomicLong;

import jlibrtp.Participant;

import org.apache.commons.lang.time.DateFormatUtils;
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
    final private int maxBufferSize;
    final private AtomicLong bufferSize = new AtomicLong();
    

    private PlayState state = PlayState.INITED;
    
    public RtspSessionListener(RtspSession mySession, int maxRtpBufferSize) {
        super();
        this.session = mySession;
        this.maxBufferSize = maxRtpBufferSize;
    }

    private Channel channel() {
        return session.channel();
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
            MediaStream stream = session.getStreams()[i];
            InterLeavedRTPSession src = srcSessions[i];
            InterLeavedRTPSession dst = dstSessions[i];
            if (null == src || null == dst) {
                continue;
            }
            
            Participant p = src.findParticipant();
            
            onNtpTimeEvent(new NtpTimeEvent(i, new NtpTime(p.lastNtpTs1, p.lastNtpTs2, p.lastRtpPkt, stream.getTimeUnit())));
        }
    }
    
    public void fireExceptionCaught(Exception ex) {
        session.channelHandlerContext().fireExceptionCaught(ex);
    }
    
    public <T> void on(RtpEvent<T> event) {
        
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


    public void onRtcpEvent(RtcpContent event) {
        logger.debug("ignore rtcp event, {}", event);
    }

    /**
     * 同步 ntp 时间. 以发送端的时间为准
     * @param event
     */
    private void onNtpTimeEvent(NtpTimeEvent event) {
        InterLeavedRTPSession rtp = session.getRTPSessions()[event.getStreamIndex()];
        NtpTime ntp = event.getSource();
        rtp.setNtpTime(ntp);
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
            sendFullRtpPkt(streamIndex, isNewFrame, rtpObj.duplicate()); // 拷贝一份，重复使用
            rtpSession.setPlayingTimestamp(rtpObj.getTimestamp());
            
            if (isNewFrame && logger.isDebugEnabled()) {
                MediaStream mediaStream = session.getStreams()[streamIndex];
                Rational timeunit = mediaStream.getTimeUnit();
                long playingTimeMillis = rtpSession.getPlayingTimeMillis(timeunit);
                String logTime = DateFormatUtils.format(playingTimeMillis, "yyyy-MM-dd HH:mm:ss.SSS");
                logger.debug("stream#{} {}, {}. {} bytes, seq = {}", streamIndex, logTime, mediaStream.getCodec(), rtpObj.dataLength(), rtpObj.getSeqNumber());
            }
        }
    }

    private void sendFullRtpPkt(int streamIndex, boolean isNewFrame, RtpPkt rtpObj) {
        InterLeavedRTPSession rtpSess = session.getRTPSessions()[streamIndex];
        
        
        // 统计流量
        if (isNewFrame) {
            rtpSess.sentPktCount ++;
        }
        rtpSess.sentOctetCount += rtpObj.dataLength();
        Participant participant = rtpSess.findParticipant();
        
       
        // 依次发送  rtp 包
        int payloadLength;
        ByteBufAllocator alloc = channel().alloc();
        payloadLength = rtpObj.content().readableBytes();
        
        final int nextSeqNo = 0xFFFF & (participant.lastSeqNumber + 1);
        participant.lastSeqNumber = nextSeqNo;
      

        if (participant.firstSeqNumber < 0) {
            participant.firstSeqNumber  = nextSeqNo;
        }

        ByteBuf payload = alloc.buffer(4 + payloadLength);
        payload.writeByte('$');
        payload.writeByte(rtpSess.rtpChannel());
        payload.writeShort(payloadLength);
        
        rtpObj.setSeqNumber(nextSeqNo);
        rtpObj.ssrc(rtpSess.ssrc());
        payload.writeBytes(rtpObj.content()); // 应为修改了 ssrc 和 seq, 所以只能用拷贝
        
        bufferSize.getAndIncrement();
        channel().writeAndFlush(payload, channel().newPromise().addListener(this));
        
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
