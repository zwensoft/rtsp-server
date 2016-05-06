package com.sengled.cloud.mediaserver.rtsp.rtp;

import io.netty.channel.Channel;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

import jlibrtp.Participant;
import jlibrtp.RTPSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sengled.cloud.mediaserver.rtsp.NtpTime;
import com.sengled.cloud.mediaserver.rtsp.Rational;

/**
 * RTP over tcp
 * @author 陈修恒
 * @date 2016年4月28日
 */
public class InterLeavedRTPSession extends RTPSession {
    private static final Logger logger = LoggerFactory.getLogger(InterLeavedRTPSession.class);
    
    private Channel channel;
    private int rtpChannel;
    
    private NtpTime ntpTime;
    private long playingTimestamp;
    
    public InterLeavedRTPSession(Channel channel, int rtpChannel, int rtcpChannel) {
        super(InterLeavedParticipantDatabase.FACTORY);
        
        this.channel = channel;
        this.rtpChannel = rtpChannel;
        this.generateCNAME();
        this.generateSsrc();
        
        this.rtcpSession = new InterLeavedRTCPSession(rtcpChannel);
    }

    public long getPlayingTimestamp() {
        return playingTimestamp;
    }
    
    public void setPlayingTimestamp(long playingTimestamp) {
        this.playingTimestamp = playingTimestamp;
    }
    
    public long getPlayingTimeMillis(Rational streamTimeUnit) {
        if (null == ntpTime) {
            return Rational.$_1_000.convert(playingTimestamp, streamTimeUnit);
        } else {
            long duration = playingTimestamp - ntpTime.getRtpTime();
            return ntpTime.getNtpTimeMillis() + Rational.$_1_000.convert(duration, streamTimeUnit);
        }
    }
    
    public void setNtpTime(NtpTime ntpTime) {
        this.ntpTime = ntpTime;
    }

    public Channel channel() {
        return channel;
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
    public void endSession() {
        // TODO Auto-generated method stub
        logger.warn("NOT Implemented!");
    }

    @Override
    protected void generateCNAME() {
        SocketAddress addr = channel.localAddress();
        
        String hostname = null;
        
        if (addr instanceof InetSocketAddress) {
            InetSocketAddress inet = (InetSocketAddress)addr;
            hostname = inet.getHostName();
        }
        
        if((null == hostname || "0.0.0.0".equals(hostname)) && System.getenv("HOSTNAME") != null) {
          hostname = System.getenv("HOSTNAME");
        }
        
        cname = System.getProperty("user.name") + "@" + hostname;
    }

   
    
    public InterLeavedRTCPSession rtcpSession() {
        return (InterLeavedRTCPSession)rtcpSession;
    }
    
    
    
    /**
     * Find out whether a participant with this SSRC is known.
     * 
     * If the user is unknown, and the system is operating in unicast mode,
     * try to match the ip-address of the sender to the ip address of a
     * previously unmatched target
     * 
     * @param ssrc the SSRC of the participant
     * @param packet the packet that notified us
     * @return the relevant participant, possibly newly created
     */
    public Participant findParticipant() {
        Participant p = partDb().getParticipant(ssrc);
        if(p == null) {
            p = new InterLeavedParticipant(this, ssrc);
            partDb().addParticipant(2,p);
        }
        return p;
    }


    
}
