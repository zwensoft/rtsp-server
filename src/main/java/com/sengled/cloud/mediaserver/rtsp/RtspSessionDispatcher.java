package com.sengled.cloud.mediaserver.rtsp;

import io.netty.util.ReferenceCountUtil;

import java.util.Iterator;
import java.util.List;

import jlibrtp.AbstractParticipant;
import jlibrtp.AbstractRtcpPkt;
import jlibrtp.AbstractRtcpPktSDES;
import jlibrtp.RtcpPktAPP;
import jlibrtp.RtcpPktBYE;
import jlibrtp.RtcpPktRR;
import jlibrtp.RtcpPktSR;
import jlibrtp.StaticProcs;
import jlibrtp.tcp.InterLeavedParticipant;
import jlibrtp.tcp.InterLeavedRTPSession;
import jlibrtp.tcp.RTCPCodec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sengled.cloud.mediaserver.rtsp.rtp.Rational;
import com.sengled.cloud.mediaserver.rtsp.rtp.RtcpContent;

/**
 * 接收客户端上传的数据, 并转发给消息的监听者
 * 
 * @author 陈修恒
 * @date 2016年4月29日
 */
public class RtspSessionDispatcher {
    private static Logger logger = LoggerFactory.getLogger(RtspSessionDispatcher.class);

    private String _name;
    private RtspSession session;

    public RtspSessionDispatcher(RtspSession session) {
        super();
        this.session = session;
        this._name = session.getName();
    }

    final public String name() {
        return null != _name ? _name : session.getName();
    }


    public <T> void dispatch(RtpEvent<T> event) {
        if (null != event) {
            try {
                if (session.isStreamSetup(event.getStreamIndex())) {
                    Sessions.getInstance().dispatch(name(), event);
                }
            } finally {
                event.destroy();
            }
        }
    }


    /**
     * 视频流停止上传了
     */
    public void teardown(String reason) {
        for (int i = 0; i < session.numStreams(); i++) {
            dispatch(new TearDownEvent(i, reason));
        }
    }
    
    /**
     * 新收到一组 RTP 帧
     * 
     * @param pkt
     */
    public void dispatch(FullRtpPkt pkt) {
        try {
            int streamIndex = session.getStreamIndex(pkt);
            if (streamIndex < 0) {
                logger.debug("stream of channel#{} NOT Found", pkt.channel());
                return;
            }

            InterLeavedRTPSession rtpSess = session.getRTPSessions()[streamIndex];
            rtpSess.sentPktCount += 1;
            rtpSess.sentOctetCount += pkt.dataLength();
            
            dispatch(new FullRtpPktEvent(streamIndex, pkt.retain()));
            logger.debug("dispatched: {}", pkt);
        } finally {
            ReferenceCountUtil.release(pkt);
        }
    }

    /**
     * 收到的 rtcp 包
     * 
     * @param rtcp rtcp 包
     */
    public void onRtcpEvent(RtcpContent rtcp) {
        try {
            int streamIndex = session.getStreamIndex(rtcp);
            if (streamIndex < 0) {
                logger.debug("stream of channel#{} NOT Found", rtcp.channel());
                return;
            }

            InterLeavedRTPSession rtpSess = session.getRTPSessions()[streamIndex];
            onRtcpEvent(streamIndex, rtpSess, rtcp.content());
        } finally {
            ReferenceCountUtil.release(rtcp);
        }
    }

    


    /**
     * Parse a received rtcp packet
     * 
     * Perform the header checks and extract the RTCP packets in it
     * 
     * @param packet the packet to be parsed
     * @return -1 if there was a problem, 0 if successfully parsed
     */
    private int onRtcpEvent(int streamIndex, InterLeavedRTPSession rtpSession, byte[] rawPkt) {

            // Parse the received compound RTCP (?) packet
            List<AbstractRtcpPkt> rtcpPkts = RTCPCodec.decode(rtpSession, rawPkt, rawPkt.length);
            logger.debug("CompRtcp: {}.", rtcpPkts);

            //Loop over the information
            Iterator<AbstractRtcpPkt> iter = rtcpPkts.iterator();

            long curTime = System.currentTimeMillis();

            while(iter.hasNext()) {
                AbstractRtcpPkt aPkt = (AbstractRtcpPkt) iter.next();

                // Our own packets should already have been filtered out.
                if(aPkt.ssrc() == rtpSession.ssrc()) {
                    logger.warn("received RTCP packet with conflicting SSRC from channel[{}]", rtpSession.rtpChannel());
                    rtpSession.resolveSsrcConflict();
                    return -1;
                }

                /**        Receiver Reports        **/
                if( aPkt instanceof RtcpPktRR) {
                    RtcpPktRR rrPkt = (RtcpPktRR) aPkt;

                    AbstractParticipant p = findParticipant(rtpSession, rrPkt.ssrc());
                    p.lastRtcpPkt = curTime;

                    if(rtpSession.rtcpAppIntf() != null) {
                        rtpSession.rtcpAppIntf().RRPktReceived(rrPkt.ssrc(), rrPkt.reporteeSsrc, 
                                rrPkt.lossFraction, rrPkt.lostPktCount, rrPkt.extHighSeqRecv,
                                rrPkt.interArvJitter, rrPkt.timeStampLSR, rrPkt.delaySR);
                    }

                    /**        Sender Reports        **/
                } else if(aPkt instanceof RtcpPktSR) {
                    RtcpPktSR srPkt = (RtcpPktSR) aPkt;

                    AbstractParticipant p = findParticipant(rtpSession, srPkt.ssrc());
                    p.lastRtcpPkt = curTime;

                    if(p != null) {

                        if(p.ntpGradient < 0 && p.lastNtpTs1 > -1) {
                            //Calculate gradient NTP vs RTP
                            long newTime = StaticProcs.undoNtpMess(srPkt.ntpTs1, srPkt.ntpTs2);
                            p.ntpGradient = ((double) (newTime - p.ntpOffset))/((double) srPkt.rtpTs - p.lastSRRtpTs);
                            logger.debug("calculated NTP vs RTP gradient:  {}", p.ntpGradient);
                        } else {
                            // Calculate sum of ntpTs1 and ntpTs2 in milliseconds
                            p.ntpOffset = StaticProcs.undoNtpMess(srPkt.ntpTs1, srPkt.ntpTs2);
                            p.lastNtpTs1 = srPkt.ntpTs1;
                            p.lastNtpTs2 = srPkt.ntpTs2;
                            p.lastSRRtpTs = srPkt.rtpTs;
                        }

                        // For the next RR
                        p.timeReceivedLSR = curTime;
                        p.setTimeStampLSR(srPkt.ntpTs1,srPkt.ntpTs2);
                        
                        logger.debug("{}", p);
                    }

                    Rational timeUnit = session.getStreams()[streamIndex].getTimeUnit();
                    NtpTimeEvent event = new NtpTimeEvent(streamIndex, new NtpTime(srPkt.ntpTs1, srPkt.ntpTs2, srPkt.rtpTs, timeUnit));
                    dispatch(event);
                    
                    logger.info("stream#{} dispatch {}", streamIndex, event);

                    /**        Source Descriptions       **/
                } else if(aPkt instanceof AbstractRtcpPktSDES) {
                    AbstractRtcpPktSDES sdesPkt = (AbstractRtcpPktSDES) aPkt;               

                    // The the participant database is updated
                    // when the SDES packet is reconstructed by CompRtcpPkt 
                    if(rtpSession.rtcpAppIntf() != null) {
                        rtpSession.rtcpAppIntf().SDESPktReceived(sdesPkt.participants());
                    }

                    /**        Bye Packets       **/
                } else if(aPkt instanceof RtcpPktBYE) {
                    RtcpPktBYE byePkt = (RtcpPktBYE) aPkt;

                    long time = System.currentTimeMillis();
                    AbstractParticipant[] partArray = new AbstractParticipant[byePkt.ssrcArray().length];

                    for(int i=0; i<byePkt.ssrcArray().length; i++) {
                        partArray[i] = rtpSession.partDb().getParticipant(byePkt.ssrcArray()[i]);
                        if(partArray[i] != null)
                            partArray[i].timestampBYE = time;
                    }

                    if(rtpSession.rtcpAppIntf() != null) {
                        rtpSession.rtcpAppIntf().BYEPktReceived(partArray, new String(byePkt.reason()));
                    }
                    
                    /**        Application specific Packets       **/
                } else if(aPkt instanceof RtcpPktAPP) {
                    RtcpPktAPP appPkt = (RtcpPktAPP) aPkt;

                    AbstractParticipant part = findParticipant(rtpSession, appPkt.ssrc());
                    
                    if(rtpSession.rtcpAppIntf() != null) {
                        rtpSession.rtcpAppIntf().APPPktReceived(part, appPkt.itemCount(), appPkt.pktName(), appPkt.pktData());
                    }
            }



            }
        return 0;
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
    private AbstractParticipant findParticipant(InterLeavedRTPSession rtpSession, long ssrc) {
        AbstractParticipant p = rtpSession.partDb().getParticipant(ssrc);
        if(p == null) {
            p = new InterLeavedParticipant(rtpSession, ssrc);
            rtpSession.partDb().addParticipant(2,p);
        }
        return p;
    }

}
