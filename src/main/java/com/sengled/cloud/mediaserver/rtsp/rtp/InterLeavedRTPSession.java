package com.sengled.cloud.mediaserver.rtsp.rtp;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPromise;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.Charset;

import jlibrtp.Participant;
import jlibrtp.RTPSession;
import jlibrtp.RtcpPkt;
import jlibrtp.RtcpPktBYE;
import jlibrtp.RtcpPktSR;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sengled.cloud.mediaserver.rtsp.MediaStream;
import com.sengled.cloud.mediaserver.rtsp.NtpTime;
import com.sengled.cloud.mediaserver.rtsp.PlayState;
import com.sengled.cloud.mediaserver.rtsp.Rational;
import com.sengled.cloud.mediaserver.rtsp.RtspSession;
import com.sengled.cloud.mediaserver.rtsp.interleaved.RtpPkt;

/**
 * RTP over tcp
 * 
 * @author 陈修恒
 * @date 2016年4月28日
 */
public class InterLeavedRTPSession extends RTPSession {
    private static final Logger logger = LoggerFactory
            .getLogger(InterLeavedRTPSession.class);

    private RtspSession rtspSession; 
    private MediaStream mediaStream;
    private int rtpChannel;

    private NtpTime ntpTime;
    private long playingTimestamp = -1;
    Participant outPart;

    private PlayState state = PlayState.BUFFERING; 
    
    public InterLeavedRTPSession(MediaStream mediaStream, RtspSession rtspSession,
            int rtpChannel, int rtcpChannel) {
        super(InterLeavedParticipantDatabase.FACTORY);

        this.mediaStream = mediaStream;
        this.rtspSession = rtspSession;
        this.rtpChannel = rtpChannel;
        this.generateCNAME();
        this.generateSsrc();

        this.outPart = new Participant(ssrc()); // rtp session use as output
                                                // part
        this.rtcpSession = new InterLeavedRTCPSession(rtcpChannel);
    }

    public long getPlayingTimestamp() {
        return playingTimestamp;
    }

    public long getPlayingTimeMillis() {
        long ntpTimestamp = playingTimestamp;
        return getNtpTimeMillis(ntpTimestamp);
    }

    public long getNtpTimeMillis(long ntpTimestamp) {
        Rational streamTimeUnit = mediaStream.getTimeUnit();
        if (null == ntpTime) {
            return Rational.$_1_000.convert(ntpTimestamp, streamTimeUnit);
        } else {
            long duration = ntpTimestamp - ntpTime.getRtpTime();
            return ntpTime.getNtpTimeMillis()
                    + Rational.$_1_000.convert(duration, streamTimeUnit);
        }
    }

    public void setNtpTime(NtpTime ntpTime) {
        this.ntpTime = ntpTime;
    }

    public NtpTime getNtpTime() {
        return ntpTime;
    }

    public void reset() {
        PlayState oldState = this.state;
        this.state = PlayState.BUFFERING;
        
        if (oldState != this.state) {
            logger.warn("state changed {}", this.state);
        }
    }


    /**
     * 收到 rtp 包的时候调用
     * 
     * @param rtpObj
     */
    public void receiveRtpPkt(RtpPkt rtpObj) {
        if (rtpObj.getTimestamp() != playingTimestamp) {
            rtpObj.setFrameStart(true);
        }
        
        this.playingTimestamp = rtpObj.getTimestamp();
    }
    
    public void sendRtpPkt(RtpPkt rtpObj,
                           GenericFutureListener<? extends Future<? super Void>> onComplete) {
        
        rtpObj = rtpObj.share();
        try {
            switch (mediaStream.getMediaType()) {
                case VIDEO:
                    sendVideoRtpPkt(rtpObj, onComplete);
                    break;
                case AUDIO:
                    sendAudioRtpPkt(rtpObj, onComplete);
                    break;
                default:
                    break;
            } 
        } finally {
            rtpObj.release();
        }
        
    }

    private void sendAudioRtpPkt(RtpPkt rtpObj,
                                 GenericFutureListener<? extends Future<? super Void>> onComplete) {
        if (state == PlayState.BUFFERING) {
            boolean hasVideo = false;
            boolean videoStarted = false;
            InterLeavedRTPSession[] subs = rtspSession.getRTPSessions();
            
            for (int i = 0; i < subs.length; i++) {
                InterLeavedRTPSession subSession = subs[i];
                if (null == subSession || subSession == this) {
                    continue;
                }
                
                if (subSession.getMediaStream().getMediaType().isVideo()) {
                    hasVideo = true;
                    videoStarted = (subSession.state == PlayState.PLAYING);
                }
            } 
            
            
            if (hasVideo && videoStarted) {
                state = PlayState.PLAYING;
            } else {
                return;
            }
        }
        
        doSendRtpPkt(rtpObj, onComplete);
    }

    private void sendVideoRtpPkt(RtpPkt rtpObj,
                                 GenericFutureListener<? extends Future<? super Void>> onComplete) {
        
        if(state == PlayState.BUFFERING) {
            // 如果是一帧的开始就可以
            if (!rtpObj.isFrameStart()) {
                return;
            }
            
            state = PlayState.PLAYING;
        }
        
        doSendRtpPkt(rtpObj, onComplete);
    }

    private boolean isH264KeyFrameStart(ByteBuf buf) {
        boolean isKeyFrame = false;
        int firstByte =  buf.readByte();
        
        int nal_type = firstByte & 0x1F;
        switch (nal_type) {
            case 5:  // IDR
            case 7:  // SPS
            case 8:  // PPS
                isKeyFrame = true;
                break;
            case 28:  // FU-A (fragmented nal)
                int fu_header = buf.readByte();
                nal_type = fu_header & 0x1f;

                switch (nal_type) {
                    case 5:  // IDR
                    case 7:  // SPS
                    case 8:  // PPS
                        isKeyFrame = true;
                        break;
                }
                break;
        }
        
        if(isKeyFrame) {
            logger.debug("key, nal_type = {}", nal_type);
        }

        return isKeyFrame;
    }

    private void doSendRtpPkt(RtpPkt rtpObj,
                           GenericFutureListener<? extends Future<? super Void>> onComplete) {
        
        // 更新播放时间
        this.playingTimestamp = rtpObj.getTimestamp();


        // 统计流量
        this.sentOctetCount += rtpObj.dataLength();
        if (rtpObj.isFrameStart()) {
            this.sentPktCount++;
        }


        final int nextSeqNo = 0xFFFF & (outPart.lastSeqNumber + 1);
        outPart.lastSeqNumber = nextSeqNo;

        if (outPart.firstSeqNumber < 0) {
            outPart.firstSeqNumber = nextSeqNo;
        }


        rtpObj.setSeqNumber(nextSeqNo);
        rtpObj.ssrc(ssrc());

        // 发送 rtcp
        if (rtpObj.isFrameStart()) {
            sendRtcpPktSRIfNeed(rtpObj.getTimestamp());
        }

        // 依次发送 rtp
        int payloadLength;
        ByteBufAllocator alloc = channel().alloc();
        payloadLength = rtpObj.content().readableBytes();

        ByteBuf interleaveHeader = alloc.buffer(4);
        interleaveHeader.writeByte('$');
        interleaveHeader.writeByte(rtpChannel());
        interleaveHeader.writeShort(payloadLength);

        logger.trace("isNew={}, {}", rtpObj.isFrameStart(), rtpObj);
        ByteBuf rtp = Unpooled.wrappedBuffer(interleaveHeader, rtpObj.content().retain());
        writeAndFlush(rtp, onComplete);
    }

    public void sendRtcpPkt(RtcpPkt sr) {
        if (state == PlayState.PLAYING) { // buffering 状态， 取消 rtcp 包的发送
            sr.encode();
            final byte[] rawPkt = sr.rawPkt;
            final int payloadLength = rawPkt.length;

            // 依次发送 rtp 包
            ByteBufAllocator alloc = channel().alloc();

            ByteBuf payload = alloc.buffer(4 + payloadLength);
            payload.writeByte('$');
            payload.writeByte(rtcpChannel());
            payload.writeShort(payloadLength);

            payload.writeBytes(rawPkt);

            if (writeAndFlush(payload, null)) {
                // 'ch{}_sent' 与 dispatch 输出的日志等长
                logger.info("stream#{} ch{}_sent {} byte(s) {}", mediaStream.getStreamIndex(),
                        rtcpChannel(), payloadLength, sr);
            } 
        }
    }

    private void sendRtcpPktSRIfNeed(long rtpTs) {
        boolean hasNtpTime = null != ntpTime
                && ntpTime.getRtpTime() <= rtpTs;
        if (!hasNtpTime) {
            return;
        }

        int duration = mediaStream.getMediaType().isVideo() ? 5 : 3;
        if (ntpTime.getNtpTs1() - outPart.lastNtpTs1 > duration) {
            long sentNtpTs1 = sendRtcpPktSR(rtpTs);
            outPart.lastNtpTs1 = sentNtpTs1;
        }
    }

    /**
     * send Rtcp SR
     * 
     * @param rtpTs
     * @return the NTP time millil sent
     */
    private long sendRtcpPktSR(long rtpTs) {
        long ntpTimeMills = getNtpTimeMillis(rtpTs);
        RtcpPktSR sr = new RtcpPktSR(ssrc(), sentPktCount, sentOctetCount, null);
        sr.rtpTs = rtpTs;
        sr.ntpTs1 = NtpTime.getNtpTs1(ntpTimeMills);
        sr.ntpTs2 = NtpTime.getNtpTs2(ntpTimeMills);

        sendRtcpPkt(sr);
        return sr.ntpTs1;
    }



    private void sendRtcpPktBye(String reason) {
        Charset utf8 = Charset.forName("UTF-8");
        RtcpPktBYE bye =
                new RtcpPktBYE(new long[] {outPart.ssrc()}, null == reason ? null
                        : reason.getBytes(utf8));

        sendRtcpPkt(bye);
    }



    private boolean writeAndFlush(ByteBuf data,
                                  GenericFutureListener<? extends Future<? super Void>> onComplete) {
        Channel channel = channel();
        if (channel.isWritable()) {
            ChannelPromise promise = channel.newPromise();
            if (null != onComplete) {
                promise.addListener(onComplete);
            }

            channel.writeAndFlush(data, promise);

            return true;
        } else {
            ReferenceCountUtil.release(data); // ignore it
            return false;
        }

    }

    public Channel channel() {
        return rtspSession.channel();
    }

    public int rtcpChannel() {
        return rtcpSession().rtcpChannel();
    }

    public int rtpChannel() {
        return rtpChannel;
    }

    public void rtpChannel(int rtpChannel) {
        this.rtpChannel = rtpChannel;
    }

    @Override
    public void endSession(String reason) {
        sendRtcpPktBye(reason);
    }

    @Override
    protected void generateCNAME() {
        SocketAddress addr = channel().localAddress();

        String hostname = null;

        if (addr instanceof InetSocketAddress) {
            InetSocketAddress inet = (InetSocketAddress) addr;
            hostname = inet.getHostName();
        }

        if ((null == hostname || "0.0.0.0".equals(hostname))
                && System.getenv("HOSTNAME") != null) {
            hostname = System.getenv("HOSTNAME");
        }

        cname = System.getProperty("user.name") + "@" + hostname;
    }

    public InterLeavedRTCPSession rtcpSession() {
        return (InterLeavedRTCPSession) rtcpSession;
    }

    /**
     * Find out whether a participant with this SSRC is known.
     * 
     * If the user is unknown, and the system is operating in unicast mode, try to match the
     * ip-address of the sender to the ip address of a previously unmatched target
     * 
     * @param ssrc the SSRC of the participant
     * @param packet the packet that notified us
     * @return the relevant participant, possibly newly created
     */
    public Participant findParticipant() {
        Participant p = partDb().getParticipant(ssrc);
        if (p == null) {
            p = new InterLeavedParticipant(this, ssrc);
            partDb().addParticipant(2, p);
        }
        return p;
    }

    public MediaStream getMediaStream() {
        return mediaStream;
    }

}
