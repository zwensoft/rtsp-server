package com.sengled.cloud.mediaserver.rtsp.rtp;

import java.util.Iterator;
import java.util.List;

import jlibrtp.Participant;
import jlibrtp.RtcpPkt;
import jlibrtp.RtcpPktSDES;
import jlibrtp.RtcpPktAPP;
import jlibrtp.RtcpPktBYE;
import jlibrtp.RtcpPktRR;
import jlibrtp.RtcpPktSR;
import jlibrtp.StaticProcs;
import jlibrtp.udp.UDPRtcpPktSDES;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 对收到的   rtcp 包进行处理
 * 
 * @author 陈修恒
 * @date 2016年4月28日
 */
public class InterLeavedRTCPReceiver {
    private static final Logger logger = LoggerFactory.getLogger(InterLeavedRTCPReceiver.class);
    
    /** Parent RTP Session */
    private InterLeavedRTPSession rtpSession = null;
    
    /** Parent RTCP Session */
    private InterLeavedRTCPSession rtcpSession = null;

    public InterLeavedRTCPReceiver(InterLeavedRTPSession rtpSession,
            InterLeavedRTCPSession rtcpSession) {
        super();
        this.rtpSession = rtpSession;
        this.rtcpSession = rtcpSession;
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
    private Participant findParticipant(long ssrc) {
        Participant p = rtpSession.partDb().getParticipant(ssrc);
        if(p == null) {
            p = new InterLeavedParticipant(rtpSession, ssrc);
            rtpSession.partDb().addParticipant(2,p);
        }
        return p;
    }

    /**
     * Parse a received rtcp packet
     * 
     * Perform the header checks and extract the RTCP packets in it
     * 
     * @param packet the packet to be parsed
     * @return -1 if there was a problem, 0 if successfully parsed
     */
    public int parsePacket(byte[] rawPkt) {

            // Parse the received compound RTCP (?) packet
            List<RtcpPkt> rtcpPkts = RTCPCodec.decode(rtpSession, rawPkt, rawPkt.length);
            logger.info("CompRtcp: {}.", rtcpPkts);

            //Loop over the information
            Iterator<RtcpPkt> iter = rtcpPkts.iterator();

            long curTime = System.currentTimeMillis();

            while(iter.hasNext()) {
                RtcpPkt aPkt = (RtcpPkt) iter.next();

                // Our own packets should already have been filtered out.
                if(aPkt.ssrc() == rtpSession.ssrc()) {
                    logger.warn("received RTCP packet with conflicting SSRC from channel[{}]", rtpSession.rtpChannel());
                    rtpSession.resolveSsrcConflict();
                    return -1;
                }

                /**        Receiver Reports        **/
                if( aPkt.getClass() == RtcpPktRR.class) {
                    RtcpPktRR rrPkt = (RtcpPktRR) aPkt;

                    Participant p = findParticipant(rrPkt.ssrc());
                    p.lastRtcpPkt = curTime;

                    if(rtpSession.rtcpAppIntf() != null) {
                        rtpSession.rtcpAppIntf().RRPktReceived(rrPkt.ssrc(), rrPkt.reporteeSsrc, 
                                rrPkt.lossFraction, rrPkt.lostPktCount, rrPkt.extHighSeqRecv,
                                rrPkt.interArvJitter, rrPkt.timeStampLSR, rrPkt.delaySR);
                    }

                    /**        Sender Reports        **/
                } else if(aPkt.getClass() == RtcpPktSR.class) {
                    RtcpPktSR srPkt = (RtcpPktSR) aPkt;

                    Participant p = findParticipant(srPkt.ssrc());
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
                    }

                    if(rtpSession.rtcpAppIntf() != null) {
                        if(srPkt.rReports() != null) {
                            rtpSession.rtcpAppIntf().SRPktReceived(srPkt.ssrc(), srPkt.ntpTs1, srPkt.ntpTs2, 
                                    srPkt.rtpTs, srPkt.sendersPktCount, srPkt.sendersPktCount,
                                    srPkt.rReports().reporteeSsrc, srPkt.rReports().lossFraction, srPkt.rReports().lostPktCount,
                                    srPkt.rReports().extHighSeqRecv, srPkt.rReports().interArvJitter, srPkt.rReports().timeStampLSR,
                                    srPkt.rReports().delaySR);
                        } else {
                            rtpSession.rtcpAppIntf().SRPktReceived(srPkt.ssrc(), srPkt.ntpTs1, srPkt.ntpTs2, 
                                    srPkt.rtpTs, srPkt.sendersPktCount, srPkt.sendersPktCount,
                                    null, null, null,
                                    null, null, null,
                                    null);
                        }
                    }

                    /**        Source Descriptions       **/
                } else if(aPkt.getClass() == UDPRtcpPktSDES.class) {
                    RtcpPktSDES sdesPkt = (RtcpPktSDES) aPkt;               

                    // The the participant database is updated
                    // when the SDES packet is reconstructed by CompRtcpPkt 
                    if(rtpSession.rtcpAppIntf() != null) {
                        rtpSession.rtcpAppIntf().SDESPktReceived(sdesPkt.participants());
                    }

                    /**        Bye Packets       **/
                } else if(aPkt.getClass() == RtcpPktBYE.class) {
                    RtcpPktBYE byePkt = (RtcpPktBYE) aPkt;

                    long time = System.currentTimeMillis();
                    Participant[] partArray = new Participant[byePkt.ssrcArray().length];

                    for(int i=0; i<byePkt.ssrcArray().length; i++) {
                        partArray[i] = rtpSession.partDb().getParticipant(byePkt.ssrcArray()[i]);
                        if(partArray[i] != null)
                            partArray[i].timestampBYE = time;
                    }

                    if(rtpSession.rtcpAppIntf() != null) {
                        rtpSession.rtcpAppIntf().BYEPktReceived(partArray, new String(byePkt.reason()));
                    }
                    
                    /**        Application specific Packets       **/
                } else if(aPkt.getClass() == RtcpPktAPP.class) {
                    RtcpPktAPP appPkt = (RtcpPktAPP) aPkt;

                    Participant part = findParticipant(appPkt.ssrc());
                    
                    if(rtpSession.rtcpAppIntf() != null) {
                        rtpSession.rtcpAppIntf().APPPktReceived(part, appPkt.itemCount(), appPkt.pktName(), appPkt.pktData());
                    }
            }



            }
        return 0;
    }
    
    /**
     * Returns a legible message when an error occurs
     * 
     * @param errorCode the internal error code, commonly negative of packet type
     * @return a string that is hopefully somewhat informative
     */
    private String debugErrorString(int errorCode) {
        String aStr = "";
        switch(errorCode) {
            case -1: aStr = "The first packet was not of type SR or RR."; break;
            case -2: aStr = "The padding bit was set for the first packet."; break;
            case -200: aStr = " Error parsing Sender Report packet."; break;
            case -201: aStr = " Error parsing Receiver Report packet."; break;
            case -202: aStr = " Error parsing SDES packet"; break;
            case -203: aStr = " Error parsing BYE packet."; break;
            case -204: aStr = " Error parsing Application specific packet."; break;
            case -205: aStr = " Error parsing RTP Feedback packet."; break;
            case -206: aStr = " Error parsing Payload-Specific Feedback packet."; break;
        default:
            aStr = "Unknown error code " + errorCode + ".";
        }
        
        return aStr;
    }
}
