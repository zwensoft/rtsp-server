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

import com.sengled.cloud.mediaserver.rtsp.event.FullRtpPktEvent;
import com.sengled.cloud.mediaserver.rtsp.event.NtpTimeEvent;
import com.sengled.cloud.mediaserver.rtsp.event.TearDownEvent;
import com.sengled.cloud.mediaserver.rtsp.interleaved.FullRtpPkt;
import com.sengled.cloud.mediaserver.rtsp.interleaved.RtcpContent;
import com.sengled.cloud.mediaserver.rtsp.interleaved.RtpPkt;
import com.sengled.cloud.mediaserver.rtsp.rtp.InterLeavedRTPSession;

public class RtspSessionListener implements GenericFutureListener<Future<? super Void>> {
    private static final Logger logger = LoggerFactory.getLogger(RtspSessionListener.class);
    
    final private RtspSession session;
    final private int maxBufferSize;
    final private AtomicLong bufferSize = new AtomicLong();
    

    private PlayState state = PlayState.WAITING_KEY_FRAME;
    
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
            RTPStream stream = session.getStreams()[i];
            InterLeavedRTPSession src = srcSessions[i];
            InterLeavedRTPSession dst = dstSessions[i];
            if (null == src || null == dst) {
                continue;
            }
            
            Participant p = src.findParticipant();
            
            syncNtpTime(new NtpTimeEvent(i, new NtpTime(p.lastNtpTs1, p.lastNtpTs2, p.lastRtpPkt, stream.getTimeUnit())));
        }
    }
    
    public void fireExceptionCaught(Exception ex) {
        session.channelHandlerContext().fireExceptionCaught(ex);
    }
    
    public <T> void on(RtpEvent<T> event) {
        
        if(event instanceof FullRtpPktEvent) {
            onFullRtpPktEvent(((FullRtpPktEvent)event));
        } else if (event instanceof NtpTimeEvent) {
            // 同步视频时间
            syncNtpTime((NtpTimeEvent)event);
            
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
    private void syncNtpTime(NtpTimeEvent event) {
        InterLeavedRTPSession rtp = session.getRTPSessions()[event.getStreamIndex()];
        Participant p = rtp.findParticipant();
        NtpTime ntp = event.getSource();
        
        p.lastRtpPkt = ntp.getRtpTime();
        p.lastNtpTs1 = ntp.getNtpTs1();
        p.lastNtpTs2 = ntp.getNtpTs2();
        logger.debug("stream#{}, {}", event.getStreamIndex(), ntp);
        
        sendRtcpPktSR(event.getStreamIndex(), rtp);
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
    
    private void onFullRtpPktEvent(FullRtpPktEvent rtpEvent) {
        boolean sent = false;
        int streamIndex = rtpEvent.getStreamIndex();
        if (session.isStreamSetup(streamIndex)) {
            FullRtpPkt fullRtp = rtpEvent.getSource();
            
            sent = sendFullRtpPkt(streamIndex, fullRtp.duplicate());
            if (!sent) {
                logger.debug("drop: {}.", fullRtp);
            } else {
                logger.debug("sent: {}.", fullRtp);
            }
        } else {
            logger.debug("stream#{} not setup", streamIndex);
        }
    }

    private boolean sendFullRtpPkt(int streamIndex, FullRtpPkt fullRtp) {
        InterLeavedRTPSession rtpSess = session.getRTPSessions()[streamIndex];
        switch (state) {
            case WAITING_KEY_FRAME:
                if (isSuitableForPlaying(fullRtp)) {
                    state = PlayState.PLAYING;
                }
                break;
            case PLAYING:
                if (bufferIfFull()) {
                    state = PlayState.BUFFER_FULL;
                }
                break;
            case BUFFER_FULL:
                if (bufferSize.get() < 1 && isSuitableForPlaying(fullRtp)) {
                    state = PlayState.PLAYING;
                } else if (bufferSize.get() < 1) {
                    state = PlayState.WAITING_KEY_FRAME;
                }
            default:
                throw new IllegalStateException("illegal PlayState[" + state + "]");
        }

        if (state != PlayState.PLAYING) {
            return false;
        }
        
        // 统计流量
        rtpSess.sentPktCount ++;
        rtpSess.sentOctetCount += fullRtp.dataLength();
        Participant participant = rtpSess.findParticipant();
        
       
        RTPStream stream = session.getStreams()[streamIndex];
        if (participant.lastRtpPkt > 0 && participant.lastRtpPkt < fullRtp.getTimestamp()) {
            long ptsOffset = stream.getTimestampMills(fullRtp.getTimestamp()  - participant.lastRtpPkt);
            long ntpTime = NtpTime.getNtpTime(participant.lastNtpTs1, participant.lastNtpTs2);
            long ptsMills = ntpTime + ptsOffset;
            logger.debug("stream#{} {} dataLength = {}, ntp-pts = {}, {} rtp(s)", 
                    streamIndex, 
                    stream.getCodec(),
                    fullRtp.dataLength(), 
                    DateFormatUtils.format(ptsMills, "yyyy-MM-dd HH:mm:ss.SSS"), 
                    fullRtp.numRtp());
        } else {
            long ptsMills = stream.getTimestampMills(fullRtp.getTimestamp());
            logger.debug("stream#{} {} dataLength = {}, raw-pts = {}, {} rtp(s)", 
                    streamIndex, 
                    stream.getCodec(),
                    fullRtp.dataLength(), 
                    DateFormatUtils.format(ptsMills, "HH:mm:ss.SSS"), 
                    fullRtp.numRtp());
        }
        
        
        // 依次发送  rtp 包
        int payloadLength;
        ByteBufAllocator alloc = channel().alloc();
        for (RtpPkt rtpObj : fullRtp.contents()) {
            payloadLength = rtpObj.content().readableBytes();
            
            int nextSeqNo = 0xFFFF & (participant.lastSeqNumber + 1);
            participant.lastSeqNumber = nextSeqNo;
            rtpObj.ssrc(rtpSess.ssrc());
            rtpObj.setSeqNumber(nextSeqNo);

            if (participant.firstSeqNumber < 0) {
                participant.firstSeqNumber  = nextSeqNo;
            }

            ByteBuf payload = alloc.buffer(4 + payloadLength);
            payload.writeByte('$');
            payload.writeByte(rtpSess.rtpChannel());
            payload.writeShort(payloadLength);
            payload.writeBytes(rtpObj.content());
            
            bufferSize.getAndIncrement();
            channel().writeAndFlush(payload, channel().newPromise().addListener(this));
        }
        
        return true;
    }

    /**
     * 可以作为媒体流的第一帧开始播放
     * 
     * @param fullRtp
     * @return
     */
    private boolean isSuitableForPlaying(FullRtpPkt fullRtp) {
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
