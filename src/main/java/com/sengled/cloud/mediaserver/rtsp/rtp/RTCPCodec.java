package com.sengled.cloud.mediaserver.rtsp.rtp;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

import jlibrtp.RtcpPkt;
import jlibrtp.RtcpPktBYE;
import jlibrtp.RtcpPktRR;
import jlibrtp.RtcpPktSR;
import jlibrtp.StaticProcs;
import jlibrtp.udp.UDPRtcpPkt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RTCPCodec {
    private static final Logger logger = LoggerFactory.getLogger(RTCPCodec.class);

    public static List<RtcpPkt> decode(InterLeavedRTPSession rtpSession, ByteBuffer rawPktBuf) {
        final byte[] rawPkt = new byte[rawPktBuf.remaining()];
        final int packetSize = rawPktBuf.remaining();
        rawPktBuf.get(rawPkt);

        return decode(rtpSession, rawPkt, packetSize);
    }


    public static List<RtcpPkt> decode(InterLeavedRTPSession rtpSessions,
                                        final byte[] rawPkt,
                                        final int packetSize) {
        List<RtcpPkt> pkts = new ArrayList<RtcpPkt>();
        try {
            doDecode(rtpSessions, rawPkt, packetSize, pkts);
        } catch (Exception e) {
            logger.warn("fail decode rtcp for {}", rtpSessions);
        }
        return pkts;
    }


    private static void doDecode(InterLeavedRTPSession rtpSessions,
                                 final byte[] rawPkt,
                                 final int packetSize,
                                 List<RtcpPkt> out) {
        // Chop it up
        int start = 0;
        int problem = 0;
        while(start < packetSize && problem == 0) {
            int length = (StaticProcs.bytesToUIntInt(rawPkt, start + 2)) + 1;
            
            if(length*4 + start > rawPkt.length) {
                logger.info("!!!! CompRtcpPkt.(rawPkt,..,..) length ({}) exceeds size of raw packet ({}) !", (length*4+start), rawPkt.length);
                problem = -3;
            }
            
            int pktType = (int) rawPkt[start + 1];
            
            if(pktType < 0) {
                pktType += 256;
            }
            
            
            if(start == 0) {
                // Compound packets need to start with SR or RR
                if(pktType != 200 && pktType != 201 ) {
                    logger.debug("!!!! CompRtcpPkt(rawPkt...) packet did not start with SR or RR");
                    problem = -1;
                }
                
                // Padding bit should be zero for the first packet
                if(((rawPkt[start] & 0x20) >>> 5) == 1) {
                    logger.debug("!!!! CompRtcpPkt(rawPkt...) first packet was padded");
                    problem = -2;
                }
            }
            
            if(pktType == 200) {
                out.add(new RtcpPktSR(rawPkt,start,length*4));
            } else if(pktType == 201 ) {
                out.add(new RtcpPktRR(rawPkt,start, -1));
            } else if(pktType == 202) {
                out.add(new InterLeavedRtcpPktSDES(rawPkt, start, rtpSessions));
            } else if(pktType == 203 ) {
                out.add(new RtcpPktBYE(rawPkt,start));
            } else {
                logger.error("!!!! CompRtcpPkt(byte[] rawPkt, int packetSize...) UNKNOWN RTCP PACKET TYPE:{}",pktType);
            }
            
            //System.out.println(" start:" + start + "  pktType:" + pktType + " length:" + length);
            
            start += length*4;
            
            logger.trace(" start:{}  parsing pktType {} length: {}", start, pktType, length);
        }
        
        
        logger.debug("<- CompRtcpPkt(rawPkt....)");
    } 
    
    
    public static byte[] encode(List<UDPRtcpPkt> rtcpPkts) {
        ListIterator<UDPRtcpPkt>  iter = rtcpPkts.listIterator();

        byte[] rawPkt = new byte[1500];
        int index = 0;
        
        while(iter.hasNext()) {
            RtcpPkt aPkt = (RtcpPkt) iter.next();
            
            if(aPkt.packetType() == 200) {
                RtcpPktSR pkt = (RtcpPktSR) aPkt;
                pkt.encode();
                System.arraycopy(pkt.rawPkt, 0, rawPkt, index, pkt.rawPkt.length);
                index += pkt.rawPkt.length;
            } else if(aPkt.packetType() == 201 ) {
                RtcpPktRR pkt = (RtcpPktRR) aPkt;
                pkt.encode();
                System.arraycopy(pkt.rawPkt, 0, rawPkt, index, pkt.rawPkt.length);
                index += pkt.rawPkt.length;
            } else if(aPkt.packetType() == 203) {
                RtcpPktBYE pkt = (RtcpPktBYE) aPkt;
                pkt.encode();
                System.arraycopy(pkt.rawPkt, 0, rawPkt, index, pkt.rawPkt.length);
                index += pkt.rawPkt.length;
            } else {
                logger.warn("CompRtcpPkt aPkt.packetType:{}", aPkt.packetType());
            }
            //System.out.println(" packetType:" + aPkt.packetType + " length:" + aPkt.rawPkt.length + " index:" + index);
        } 
        
        byte[] output = new byte[index];
        
        System.arraycopy(rawPkt, 0, output, 0, index);

        logger.info("-> CompRtcpPkt.encode()");
        return output;
    }
}
