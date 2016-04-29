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

import java.net.InetAddress;

import jlibrtp.AbstractParticipantDatabase;
import jlibrtp.AbstractRtcpPkt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/** 
 * Common RTCP packet headers.
 *
 * @author Arne Kepp
 */
public class RtcpPkt extends AbstractRtcpPkt {
	static final Logger logger = LoggerFactory.getLogger(RtcpPkt.class);
	
	
	/**
	 * Check whether this packet came from the source we expected.
	 * 
	 * Not currently used!
	 * 
	 * @param adr address that packet came from
	 * @param partDb the participant database for the session
	 * @return true if this packet came from the expected source
	 */
	protected boolean check(InetAddress adr, AbstractParticipantDatabase partDb) {
		//Multicast -> We have to be naive
		if (partDb.rtpSession.mcSession() && adr.equals(((UDPRTPSession)partDb.rtpSession).mcGroup))
			return true;
		
		//See whether this participant is known
		Participant part = (Participant)partDb.getParticipant(this.ssrc);
		if(part != null && part.rtcpAddress.getAddress().equals(adr))
			return true;
		
		//If not, we should look for someone without SSRC with his ip-address?
		return false;
	}
}
