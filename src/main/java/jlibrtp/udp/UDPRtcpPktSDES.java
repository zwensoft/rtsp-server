/**
 * Java RTP Library (jlibrtp)
 * Copyright (C) 2006 Arne Kepp
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package jlibrtp.udp;

import java.net.InetSocketAddress;

import jlibrtp.AbstractParticipant;
import jlibrtp.AbstractParticipantDatabase;
import jlibrtp.AbstractRTPSession;
import jlibrtp.AbstractRtcpPktSDES;
import jlibrtp.AbstractRtcpPktSDES.ParticipantFactory;


/**
 * RTCP packets for Source Descriptions
 * 
 * @author Arne Kepp
 */
public class UDPRtcpPktSDES extends AbstractRtcpPktSDES {
    
    /**
     * Constructor to create a new SDES packet
     * 
     * TODO:
     * Currently the added participants are not actually encoded
     * because the library lacks some support for acting as mixer or
     * relay in other areas.
     * 
     * @param reportThisSession include information from RTPSession as a participant
     * @param rtpSession the session itself
     * @param additionalParticipants additional participants to include
     */
    protected UDPRtcpPktSDES(boolean reportThisSession, AbstractRTPSession rtpSession, AbstractParticipant[] additionalParticipants) {
        super(reportThisSession, rtpSession, additionalParticipants);
    }
    
	/**
	 * Constructor that parses a received packet
	 * 
	 * @param aRawPkt the byte[] containing the packet
	 * @param start where in the byte[] this packet starts
	 * @param socket the address from which the packet was received
	 * @param partDb the participant database
	 */
	protected UDPRtcpPktSDES(byte[] aRawPkt,int start, final InetSocketAddress socket, AbstractParticipantDatabase partDb) {
		super(aRawPkt, start, partDb,new ParticipantFactory() {
            
            @Override
            public AbstractParticipant newInstance(long SSRC) {
                return new Participant(socket, socket, SSRC);
            }
        });
	}

	
	/**
	 * Debug purposes only
	 */
	public void debugPrint() {
		System.out.println("RtcpPktSDES.debugPrint() ");
		if(participants != null) {
			for(int i= 0; i<participants.length; i++) {
				AbstractParticipant part = participants[i];
				System.out.println("     part.ssrc: " + part.ssrc() + "  part.cname: " + part.cname + " part.loc: " + part.loc);
			}
		} else {
			System.out.println("     nothing to report (only valid for received packets)");
		}
	}
}
