package com.sengled.cloud.mediaserver.rtsp;

import io.netty.util.ReferenceCountUtil;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;

import jlibrtp.Participant;
import jlibrtp.RtcpPkt;
import jlibrtp.RtcpPktAPP;
import jlibrtp.RtcpPktBYE;
import jlibrtp.RtcpPktRR;
import jlibrtp.RtcpPktSDES;
import jlibrtp.RtcpPktSR;
import jlibrtp.StaticProcs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sengled.cloud.async.TimerExecutor;
import com.sengled.cloud.mediaserver.rtsp.ServerEngine.Dispatcher;
import com.sengled.cloud.mediaserver.rtsp.event.NtpTimeEvent;
import com.sengled.cloud.mediaserver.rtsp.event.RtpPktEvent;
import com.sengled.cloud.mediaserver.rtsp.event.TearDownEvent;
import com.sengled.cloud.mediaserver.rtsp.interleaved.RtcpContent;
import com.sengled.cloud.mediaserver.rtsp.interleaved.RtpPkt;
import com.sengled.cloud.mediaserver.rtsp.rtp.InterLeavedParticipant;
import com.sengled.cloud.mediaserver.rtsp.rtp.InterLeavedRTPSession;
import com.sengled.cloud.mediaserver.rtsp.rtp.RTCPCodec;

/**
 * 接收客户端上传的数据, 并转发给消息的监听者
 * 
 * @author 陈修恒
 * @date 2016年4月29日
 */
public class RtspSessionDispatcher {
    private static Logger logger = LoggerFactory.getLogger(RtspSessionDispatcher.class);
    final private static TimerExecutor tasker = new TimerExecutor();
    
    private RtspSession session;
    private Dispatcher proxy;

    public RtspSessionDispatcher(final Dispatcher dispatcher, final RtspSession session) {
        super();
        this.proxy = dispatcher;
        this.session = session;
        
        tasker.setInterval(new Callable<Boolean>() {
            
            @Override
            public Boolean call() throws Exception {
                if (session.isDestroyed()) {
                    return true;
                }
                
                sendRtcpPktRR();
                return null;
            }

        }, 5000, 5000);
    }


    public <T> void dispatch(RtpEvent<T> event) {
        if (null != event) {
            try {
                if (session.isStreamSetup(event.getStreamIndex())
                        || event.getStreamIndex() < 0) {
                    proxy.dispatch(event);
                }
            } finally {
                event.destroy();
            }
        }
    }


    
    /**
     * 新收到一组 RTP 帧
     * 
     * @param rtpObj
     */
    public void dispatch(RtpPkt rtpObj) {
        try {
            int streamIndex = session.getStreamIndex(rtpObj);
            if (streamIndex < 0) {
                logger.debug("stream of channel#{} NOT Found", rtpObj.channel());
                return;
            }

            InterLeavedRTPSession rtpSess = session.getRTPSessions()[streamIndex];
            rtpSess.receiveRtpPkt(rtpObj);
            
            logger.debug("dispatch: {}", rtpObj);
            dispatch(new RtpPktEvent(streamIndex, rtpObj.retain()));
        } finally {
            ReferenceCountUtil.release(rtpObj);
        }
    }
    

    private void sendRtcpPktRR() {
        InterLeavedRTPSession[] rtpSessions = session.getRTPSessions();
        for (int i = 0; i < rtpSessions.length; i++) {
            InterLeavedRTPSession rtpSess = rtpSessions[i];
            if (null == rtpSess) {
                continue;
            }
            
            Participant part = findParticipant(rtpSess, rtpSess.ssrc());

            RtcpPktRR rr = new RtcpPktRR(new Participant[]{part}, rtpSess.ssrc());
            rr.encode();
        }
        
    }
    
    /**
     * 收到的 rtcp 包
     * 
     * @param rtcp rtcp 包
     */
    public void receiveRtcpEvent(RtcpContent rtcp) {
        try {
            int streamIndex = session.getStreamIndex(rtcp);
            if (streamIndex < 0) {
                logger.debug("stream of channel#{} NOT Found", rtcp.channel());
                return;
            }
    
            InterLeavedRTPSession rtpSess = session.getRTPSessions()[streamIndex];
            onRtcpEvent(streamIndex, rtpSess, rtcp.readBytes());
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
            List<RtcpPkt> rtcpPkts = RTCPCodec.decode(rtpSession, rawPkt, rawPkt.length);
            logger.debug("CompRtcp: {}.", rtcpPkts);

            //Loop over the information
            Iterator<RtcpPkt> iter = rtcpPkts.iterator();

            long curTime = System.currentTimeMillis();

            while(iter.hasNext()) {
                RtcpPkt aPkt = (RtcpPkt) iter.next();

                /**        Receiver Reports        **/
                if( aPkt instanceof RtcpPktRR) {
                    RtcpPktRR rrPkt = (RtcpPktRR) aPkt;

                    Participant p = findParticipant(rtpSession, rrPkt.ssrc());
                    p.lastRtcpPkt = curTime;

                    if(rtpSession.rtcpAppIntf() != null) {
                        rtpSession.rtcpAppIntf().RRPktReceived(rrPkt.ssrc(), rrPkt.reporteeSsrc, 
                                rrPkt.lossFraction, rrPkt.lostPktCount, rrPkt.extHighSeqRecv,
                                rrPkt.interArvJitter, rrPkt.timeStampLSR, rrPkt.delaySR);
                    }

                    /**        Sender Reports        **/
                } else if(aPkt instanceof RtcpPktSR) {
                    RtcpPktSR srPkt = (RtcpPktSR) aPkt;

                    Participant p = rtpSession.findParticipant();
                    p.lastRtcpPkt = curTime;

                    if(p != null) {
                        // Calculate sum of ntpTs1 and ntpTs2 in milliseconds
                        p.ntpOffset = StaticProcs.undoNtpMess(srPkt.ntpTs1, srPkt.ntpTs2);
                        p.lastNtpTs1 = srPkt.ntpTs1;
                        p.lastNtpTs2 = srPkt.ntpTs2;
                        p.lastSRRtpTs = srPkt.rtpTs;

                        // For the next RR
                        p.timeReceivedLSR = curTime;
                        p.setTimeStampLSR(srPkt.ntpTs1,srPkt.ntpTs2);
                    }

                    NtpTimeEvent event = new NtpTimeEvent(streamIndex, new NtpTime(srPkt.ntpTs1, srPkt.ntpTs2, srPkt.rtpTs));
                    logger.debug("{}", srPkt);
                    logger.debug("stream#{} dispatch {}", streamIndex, event.getSource());

                    dispatch(event);

                    /**        Source Descriptions       **/
                } else if(aPkt instanceof RtcpPktSDES) {
                    RtcpPktSDES sdesPkt = (RtcpPktSDES) aPkt;               

                    // The the participant database is updated
                    // when the SDES packet is reconstructed by CompRtcpPkt 
                    if(rtpSession.rtcpAppIntf() != null) {
                        rtpSession.rtcpAppIntf().SDESPktReceived(sdesPkt.participants());
                    }

                    /**        Bye Packets       **/
                } else if(aPkt instanceof RtcpPktBYE) {
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

                    TearDownEvent event = new TearDownEvent(byePkt.reason());
                    dispatch(event);
                    
                    logger.info("stream#{} dispatch {}", streamIndex, event.getSource());
                    
                    /**        Application specific Packets       **/
                } else if(aPkt instanceof RtcpPktAPP) {
                    RtcpPktAPP appPkt = (RtcpPktAPP) aPkt;

                    Participant part = findParticipant(rtpSession, appPkt.ssrc());
                    
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
    private Participant findParticipant(InterLeavedRTPSession rtpSession, long ssrc) {
        Participant p = rtpSession.partDb().getParticipant(ssrc);
        if(p == null) {
            p = new InterLeavedParticipant(rtpSession, ssrc);
            rtpSession.partDb().addParticipant(2,p);
        }
        return p;
    }

}
