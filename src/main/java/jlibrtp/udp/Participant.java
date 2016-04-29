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
import jlibrtp.PktBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A participant represents a peer in an RTPSession. Based on the information stored on 
 * these objects, packets are processed and statistics generated for RTCP.
 */
public class Participant extends AbstractParticipant {
	private static Logger logger = LoggerFactory.getLogger(Participant.class);
	
	/** Where to send RTP packets (unicast)*/
	protected InetSocketAddress rtpAddress = null; 
	/** Where to send RTCP packets (unicast) */
	protected InetSocketAddress rtcpAddress = null;
	/** Where the first RTP packet was received from */
	protected InetSocketAddress rtpReceivedFromAddress = null;
	/** Where the first RTCP packet was received from */
	protected InetSocketAddress rtcpReceivedFromAddress = null;
	
	/**
	 * Create a basic participant. If this is a <b>unicast</b> session you must provide network address (ipv4 or ipv6) and ports for RTP and RTCP, 
	 * as well as a cname for this contact. These things should be negotiated through SIP or a similar protocol.
	 * 
	 * jlibrtp will listen for RTCP packets to obtain a matching SSRC for this participant, based on cname.
	 * @param networkAddress string representation of network address (ipv4 or ipv6). Use "127.0.0.1" for multicast session.
	 * @param rtpPort port on which peer expects RTP packets. Use 0 if this is a sender-only, or this is a multicast session.
	 * @param rtcpPort port on which peer expects RTCP packets. Use 0 if this is a sender-only, or this is a multicast session.
	 */
	public Participant(String networkAddress, int rtpPort, int rtcpPort) {
		// RTP
		if(rtpPort > 0) {
			try {
				rtpAddress = new InetSocketAddress(networkAddress, rtpPort);
			} catch (Exception e) {
				logger.error("Couldn't resolve rtp {}:{}", networkAddress, rtpPort);
			} 
			//isReceiver = true;
		}
		
		// RTCP 
		if(rtcpPort > 0) {
			try {
				rtcpAddress = new InetSocketAddress(networkAddress, rtcpPort);
			} catch (Exception e) {
				logger.error("Couldn't resolve rtcp {}:{}", networkAddress, rtcpPort);
			}
		}
		
		// first seq number
		this.firstSeqNumber = firstSeqNumber;

		//By default this is a sender
		//isSender = true;
	}
	
	// We got a packet, but we don't know this person yet.
	protected Participant(InetSocketAddress rtpAdr, InetSocketAddress rtcpAdr, long SSRC) {
	    super(SSRC);
		rtpReceivedFromAddress = rtpAdr;
		rtcpReceivedFromAddress = rtcpAdr;

	}
	
	
	/**
	 * RTP Address registered with this participant.
	 * 
	 * @return address of participant
	 */
	InetSocketAddress getRtpSocketAddress() {
		return rtpAddress;
	}
	
	
	/**
	 * RTCP Address registered with this participant.
	 * 
	 * @return address of participant
	 */
	InetSocketAddress getRtcpSocketAddress() {
		return rtcpAddress;
	}

	/**
	 * InetSocketAddress this participant has used to
	 * send us RTP packets.
	 * 
	 * @return address of participant
	 */
	InetSocketAddress getRtpReceivedFromAddress() {
		return rtpAddress;
	}

	
	
	/**
	 * InetSocketAddress this participant has used to
	 * send us RTCP packets.
	 * 
	 * @return address of participant
	 */
	InetSocketAddress getRtcpReceivedFromAddress() {
		return rtcpAddress;
	}
	
	
	/**
	 * Only for debugging purposes
	 */
	public void debugPrint() {
		System.out.print(" Participant.debugPrint() SSRC:"+this.ssrc+" CNAME:"+this.cname);
		if(this.rtpAddress != null)
			System.out.print(" RTP:"+this.rtpAddress.toString());
		if(this.rtcpAddress != null)
			System.out.print(" RTCP:"+this.rtcpAddress.toString());
		System.out.println("");
		
		System.out.println("                          Packets received:"+this.receivedPkts);
	}

}
