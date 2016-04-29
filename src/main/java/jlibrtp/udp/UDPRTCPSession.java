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

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;

import jlibrtp.RTCPSession;


/**
 * This class acts as an organizer for most of the information
 * and functions pertaining to RTCP packet generation and reception 
 * 
 * @author Arne Kepp
 *
 */
public class UDPRTCPSession extends RTCPSession {
	/** Unicast socket */
	protected DatagramSocket rtcpSock = null;
	/** Multicast socket */
	protected MulticastSocket rtcpMCSock = null;
	/** Multicast group */
	protected InetAddress mcGroup = null;

	/** RTCP Receiver thread */
	protected RTCPReceiverThread recvThrd = null;
	/** RTCP Sender thread */
	protected RTCPSenderThread senderThrd = null;
	
	/**
	 * Constructor for unicast sessions
	 * 
	 * @param parent RTPSession that started this
	 * @param rtcpSocket the socket to use for listening and sending
	 */
	public UDPRTCPSession(UDPRTPSession parent, DatagramSocket rtcpSocket) {
		this.rtcpSock = rtcpSocket;
		rtpSession = parent;
	}

	/**
	 * Constructor for multicast sessions
	 * 
	 * @param parent parent RTPSession
	 * @param rtcpSocket parent RTPSession that started this
	 * @param multicastGroup multicast group to bind the socket to
	 */
	public UDPRTCPSession(UDPRTPSession parent, MulticastSocket rtcpSocket, InetAddress multicastGroup) {
		mcGroup = multicastGroup;
		this.rtcpSock = rtcpSocket;
		rtpSession = parent;
	}

	/**
	 * Starts the session, calculates delays and fires up the threads.
	 *
	 */
	@Override
    public void start() {
		//nextDelay = 2500 + rtpSession.random.nextInt(1000) - 500;
		this.calculateDelay();
		recvThrd = new RTCPReceiverThread(this, ((UDPRTPSession)this.rtpSession));
		senderThrd = new RTCPSenderThread(this, ((UDPRTPSession)this.rtpSession));
		recvThrd.start();
		senderThrd.start();
	}

	/**
	 * Send bye packets, handled by RTCP Sender thread
	 *
	 */
	@Override
    public void sendByes() {
		senderThrd.sendByes();
	}
	
	
	/**
	 * Wake the sender thread because of this ssrc
	 * 
	 * @param ssrc that has feedback waiting.
	 */
	public void wakeSenderThread(long ssrc) {
		this.fbWaiting = ssrc;
		this.senderThrd.interrupt();
		
		// Give it a chance to catch up
		try { Thread.sleep(0,1); } catch (Exception e){ };
	}

}

