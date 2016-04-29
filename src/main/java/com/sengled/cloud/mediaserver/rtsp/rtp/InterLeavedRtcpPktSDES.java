package com.sengled.cloud.mediaserver.rtsp.rtp;

import jlibrtp.Participant;
import jlibrtp.RtcpPktSDES;

public class InterLeavedRtcpPktSDES extends RtcpPktSDES {

    /**
     * Constructor that parses a received packet
     * 
     * @param aRawPkt the byte[] containing the packet
     * @param start where in the byte[] this packet starts
     * @param socket the address from which the packet was received
     * @param partDb the participant database
     */
    protected InterLeavedRtcpPktSDES(byte[] aRawPkt,int start, final InterLeavedRTPSession rtpSession) {
        super(aRawPkt, start, rtpSession.partDb(), new ParticipantFactory() {
            
            @Override
            public Participant newInstance(long SSRC) {
                return new InterLeavedParticipant(rtpSession, SSRC);
            }
        });
    }

}
