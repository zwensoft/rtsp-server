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

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.util.Iterator;

import jlibrtp.AbstractParticipant;
import jlibrtp.AbstractRTPSession;
import jlibrtp.AppCallerThread;
import jlibrtp.DebugAppIntf;
import jlibrtp.RTCPAVPFIntf;
import jlibrtp.RTCPAppIntf;
import jlibrtp.RTPAppIntf;
import jlibrtp.RtpPkt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * The RTPSession object is the core of jlibrtp. 
 * 
 * One should be instantiated for every communication channel, i.e. if you send voice and video, you should create one for each.
 * 
 * The instance holds a participant database, as well as other information about the session. When the application registers with the session, the necessary threads for receiving and processing RTP packets are spawned.
 * 
 * RTP Packets are sent synchronously, all other operations are asynchronous.
 * 
 * @author Arne Kepp
 */
public class UDPRTPSession extends AbstractRTPSession {
	 static final Logger logger = LoggerFactory.getLogger(UDPRTPSession.class);
	 
    /** Whether this session is a multicast session or not */
    protected boolean mcSession = false;
    
	 /** RTP unicast socket */
	 protected DatagramSocket rtpSock = null;
	 /** RTP multicast socket */
	 protected MulticastSocket rtpMCSock = null;
	 /** RTP multicast group */
	 protected InetAddress mcGroup = null;
	 

	 
	 /** The thread for receiving RTP packets */
	 protected RTPReceiverThread recvThrd = null;
	 /** The thread for invoking callbacks for RTP packets */
	 protected AppCallerThread appCallerThrd = null;
	 
	 /**
	  * Returns an instance of a <b>unicast</b> RTP session. 
	  * Following this you should adjust any settings and then register your application.
	  * 
	  * The sockets should have external ip addresses, else your CNAME automatically
	  * generated CNAMe will be bad.
	  * 
	  * @param	rtpSocket UDP socket to receive RTP communication on
	  * @param	rtcpSocket UDP socket to receive RTCP communication on, null if none.
	  */
	 public UDPRTPSession(DatagramSocket rtpSocket, DatagramSocket rtcpSocket) {
	     super(UDPParticipantDatabase.FACTORY);
	     
		 mcSession = false;
		 rtpSock = rtpSocket;
		 this.generateCNAME();
		 this.generateSsrc();
		 this.rtcpSession = new UDPRTCPSession(this,rtcpSocket);
		 
		 // The sockets are not always imediately available?
		 try { Thread.sleep(1); } catch (InterruptedException e) { System.out.println("RTPSession sleep failed"); }
	 }
	 
	 /**
	  * Returns an instance of a <b>multicast</b> RTP session. 
	  * Following this you should register your application.
	  * 
	  * The sockets should have external ip addresses, else your CNAME automatically
	  * generated CNAMe will be bad.
	  * 
	  * @param	rtpSock a multicast socket to receive RTP communication on
	  * @param	rtcpSock a multicast socket to receive RTP communication on
	  * @param	multicastGroup the multicast group that we want to communicate with.
	  */
	 public UDPRTPSession(MulticastSocket rtpSock, MulticastSocket rtcpSock, InetAddress multicastGroup) throws Exception {
	     super(UDPParticipantDatabase.FACTORY);
	         
	     
		 mcSession = true;
		 rtpMCSock =rtpSock;
		 mcGroup = multicastGroup;
		 rtpMCSock.joinGroup(mcGroup);
		 rtcpSock.joinGroup(mcGroup);
		 this.generateCNAME();
		 this.generateSsrc();
		 this.rtcpSession = new UDPRTCPSession(this,rtcpSock,mcGroup);
		 
		 // The sockets are not always imediately available?
		 try { Thread.sleep(1); } catch (InterruptedException e) { System.out.println("RTPSession sleep failed"); }
	 }
	 public boolean mcSession() {
        return mcSession;
    }
	 /**
	  * Registers an application (RTPAppIntf) with the RTP session.
	  * The session will call receiveData() on the supplied instance whenever data has been received.
	  * 
	  * Following this you should set the payload type and add participants to the session.
	  * 
	  * @param	rtpApp an object that implements the RTPAppIntf-interface
	  * @param	rtcpApp an object that implements the RTCPAppIntf-interface (optional)
	  * @return	-1 if this RTPSession-instance already has an application registered.
	  */
	 public int RTPSessionRegister(RTPAppIntf rtpApp, RTCPAppIntf rtcpApp, DebugAppIntf debugApp) {
		if(registered) {
			logger.error("Can\'t register another application!");
			return -1;
		} else {
			registered = true;
			generateSeqNum();
			
			this.appIntf = rtpApp;
			this.rtcpAppIntf = rtcpApp;
			this.debugAppIntf = debugApp;
			
			recvThrd = new RTPReceiverThread(this);
			appCallerThrd = new AppCallerThread(this, rtpApp);
			recvThrd.start();
		 	appCallerThrd.start();
		 	rtcpSession.start();
		 	return 0;
		}
	}
	
	 /**
	 * Change the RTP socket of the session. 
	 * Peers must be notified through SIP or other signalling protocol.
	 * Only valid if this is a unicast session to begin with.
	 * 
	 * @param newSock integer for new port number, check it is free first.
	 */
	public int updateRTPSock(DatagramSocket newSock) {
		if(!mcSession) {
			 rtpSock = newSock;
			 return 0;
		} else {
			System.out.println("Can't switch from multicast to unicast.");
			return -1;
		}
	}
	
	/**
	 * Change the RTCP socket of the session. 
	 * Peers must be notified through SIP or other signalling protocol.
	 * Only valid if this is a unicast session to begin with.
	 * 
	 * @param newSock the new unicast socket for RTP communication.
	 */
	public int updateRTCPSock(DatagramSocket newSock) {
		if(!mcSession) {
			((UDPRTCPSession)this.rtcpSession).rtcpSock = newSock;
			return 0;
		} else {
			System.out.println("Can't switch from multicast to unicast.");
			return -1;
		}
	}
	
	/**
	 * Change the RTP multicast socket of the session. 
	 * Peers must be notified through SIP or other signalling protocol.
	 * Only valid if this is a multicast session to begin with.
	 * 
	 * @param newSock the new multicast socket for RTP communication.
	 */
	public int updateRTPSock(MulticastSocket newSock) {
		if(mcSession) {
			 this.rtpMCSock = newSock;
			 return 0;
		} else {
			System.out.println("Can't switch from unicast to multicast.");
			return -1;
		}
	}
	
	/**
	 * Change the RTCP multicast socket of the session. 
	 * Peers must be notified through SIP or other signalling protocol.
	 * Only valid if this is a multicast session to begin with.
	 * 
	 * @param newSock the new multicast socket for RTCP communication.
	 */
	public int updateRTCPSock(MulticastSocket newSock) {
		if(mcSession) {
		    ((UDPRTCPSession)this.rtcpSession).rtcpMCSock = newSock;
			return 0;
		} else {
			System.out.println("Can't switch from unicast to multicast.");
			return -1;
		}
	}
	
	/**
	 * Set whether the stack should operate in RFC 4585 mode.
	 * 
	 * This will automatically call adjustPacketBufferBehavior(-1),
	 * i.e. disable all RTP packet buffering in jlibrtp,
	 * and disable frame reconstruction 
	 * 
	 * @param rtcpAVPFIntf the in
	 */
	public int registerAVPFIntf(RTCPAVPFIntf rtcpAVPFIntf, int maxDelay, int earlyThreshold, int regularThreshold ) {
		if(this.rtcpSession != null) {
			this.packetBufferBehavior(-1);
			this.frameReconstruction = false;
			this.rtcpAVPFIntf = rtcpAVPFIntf;
			this.fbEarlyThreshold = earlyThreshold;
			this.fbRegularThreshold = regularThreshold;	
			return 0;
		} else {
			return -1;
		}
	}
	
	/**
	 * Unregisters the RTCP AVPF interface, thereby going from
	 * RFC 4585 mode to RFC 3550
	 * 
	 * You still have to adjust packetBufferBehavior() and
	 * frameReconstruction.
	 * 	
	 */
	public void unregisterAVPFIntf() {
		this.fbEarlyThreshold = -1;
		this.fbRegularThreshold = -1;	
		this.rtcpAVPFIntf = null;
	}
	

    public void endSession() {
        this.endSession = true;
        
        // No more RTP packets, please
        if(this.mcSession) {
            this.rtpMCSock.close();
        } else {
            this.rtpSock.close();
        }
        
        // Signal the thread that pushes data to application
        this.pktBufLock.lock();
        try { this.pktBufDataReady.signalAll(); } finally {
            this.pktBufLock.unlock();
        }
        // Interrupt what may be sleeping
        ((UDPRTCPSession)this.rtcpSession).senderThrd.interrupt();
        
        // Give things a chance to cool down.
        try { Thread.sleep(50); } catch (Exception e){ };
        
        this.appCallerThrd.interrupt();
    
        // Give things a chance to cool down.
        try { Thread.sleep(50); } catch (Exception e){ };
        
        if(this.rtcpSession != null) {      
            // No more RTP packets, please
            if(this.mcSession) {
                ((UDPRTCPSession)this.rtcpSession).rtcpMCSock.close();
            } else {
                ((UDPRTCPSession)this.rtcpSession).rtcpSock.close();
            }
        }
    }
    
    
    protected void generateCNAME() {
        String hostname;
        
        if(this.mcSession) {
            hostname = this.rtpMCSock.getLocalAddress().getCanonicalHostName();
        } else {
            hostname = this.rtpSock.getLocalAddress().getCanonicalHostName();
        }
        
        //if(hostname.equals("0.0.0.0") && System.getenv("HOSTNAME") != null) {
        //  hostname = System.getenv("HOSTNAME");
        //}
        
        cname = System.getProperty("user.name") + "@" + hostname;
    }
    

    /**
      * Send data to all participants registered as receivers, using the current timeStamp,
      * dynamic sequence number and the current payload type specified for the session.
      * 
      * @param buf A buffer of bytes, less than 1496 bytes
      * @return null if there was a problem, {RTP Timestamp, Sequence number} otherwise
      */
    public long[] sendData(byte[] buf) {
         byte[][] tmp = {buf}; 
         long[][] ret = this.sendData(tmp, null, null, -1, null);
         
         if(ret != null)
             return ret[0];
         
         return null;
     }

    /**
      * Send data to all participants registered as receivers, using the specified timeStamp,
      * sequence number and the current payload type specified for the session.
      * 
      * @param buf A buffer of bytes, less than 1496 bytes
      * @param rtpTimestamp the RTP timestamp to be used in the packet
      * @param seqNum the sequence number to be used in the packet
      * @return null if there was a problem, {RTP Timestamp, Sequence number} otherwise
      */
    public long[] sendData(byte[] buf,
                            long rtpTimestamp,
                            long seqNum) {
                                 byte[][] tmp = {buf};
                                 long[][] ret = this.sendData(tmp, null, null, -1, null);
                                 
                                 if(ret != null)
                                     return ret[0];
                                 
                                 return null;
                             }


    /**
      * Send data to all participants registered as receivers, using the current timeStamp and
      * payload type. The RTP timestamp will be the same for all the packets.
      * 
      * @param buffers A buffer of bytes, should not bed padded and less than 1500 bytes on most networks.
      * @param csrcArray an array with the SSRCs of contributing sources
      * @param markers An array indicating what packets should be marked. Rarely anything but the first one
      * @param rtpTimestamp The RTP timestamp to be applied to all packets
      * @param seqNumbers An array with the sequence number associated with each byte[]
      * @return null if there was a problem sending the packets, 2-dim array with {RTP Timestamp, Sequence number}
      */
    public long[][] sendData(byte[][] buffers,
                            long[] csrcArray,
                            boolean[] markers,
                            long rtpTimestamp,
                            long[] seqNumbers) {
                                logger.debug("-> RTPSession.sendData(byte[])");
                            
                                 // Same RTP timestamp for all
                                 if(rtpTimestamp < 0)
                                     rtpTimestamp = System.currentTimeMillis();
                                 
                                 // Return values
                                 long[][] ret = new long[buffers.length][2];
                            
                                 for(int i=0; i<buffers.length; i++) {
                                     byte[] buf = buffers[i];
                                     
                                     boolean marker = false;
                                     if(markers != null)
                                          marker = markers[i];
                                     
                                     if(buf.length > 1500) {
                                         System.out.println("RTPSession.sendData() called with buffer exceeding 1500 bytes ("+buf.length+")");
                                     }
                            
                                     // Get the return values
                                     ret[i][0] = rtpTimestamp;
                                     if(seqNumbers == null) {
                                         ret[i][1] = getNextSeqNum();
                                     } else {
                                         ret[i][1] = seqNumbers[i];
                                     }
                                     // Create a new RTP Packet
                                     RtpPkt pkt = new RtpPkt(rtpTimestamp,this.ssrc,(int) ret[i][1],this.payloadType,buf);
                            
                                     if(csrcArray != null)
                                         pkt.setCsrcs(csrcArray);
                            
                                     pkt.setMarked(marker);
                            
                                     // Creates a raw packet
                                     byte[] pktBytes = pkt.encode();
                                     
                                     //System.out.println(Integer.toString(StaticProcs.bytesToUIntInt(pktBytes, 2)));
                            
                                     // Pre-flight check, are resolving an SSRC conflict?
                                     if(this.conflict) {
                                         System.out.println("RTPSession.sendData() called while trying to resolve conflict.");
                                         return null;
                                     }
                            
                            
                                     if(this.mcSession) {
                                         DatagramPacket packet = null;
                            
                            
                                         try {
                                             packet = new DatagramPacket(pktBytes,pktBytes.length,this.mcGroup,this.rtpMCSock.getPort());
                                         } catch (Exception e) {
                                             System.out.println("RTPSession.sendData() packet creation failed.");
                                             e.printStackTrace();
                                             return null;
                                         }
                            
                                         try {
                                             rtpMCSock.send(packet);
                                             //Debug
                                             if(this.debugAppIntf != null) {
                                                 this.debugAppIntf.packetSent(1, (InetSocketAddress) packet.getSocketAddress(), 
                                                         new String("Sent multicast RTP packet of size " + packet.getLength() + 
                                                                 " to " + packet.getSocketAddress().toString() + " via " 
                                                                 + rtpMCSock.getLocalSocketAddress().toString()));
                                             }
                                         } catch (Exception e) {
                                             System.out.println("RTPSession.sendData() multicast failed.");
                                             e.printStackTrace();
                                             return null;
                                         }      
                            
                                     } else {
                                         // Loop over recipients
                                         Iterator<AbstractParticipant> iter = partDb.getUnicastReceivers();
                                         while(iter.hasNext()) {            
                                             InetSocketAddress receiver = ((Participant)iter.next()).rtpAddress;
                                             DatagramPacket packet = null;
                            
                                             logger.debug("   Sending to {}", receiver);
                                             
                            
                                             try {
                                                 packet = new DatagramPacket(pktBytes,pktBytes.length,receiver);
                                             } catch (Exception e) {
                                                 System.out.println("RTPSession.sendData() packet creation failed.");
                                                 e.printStackTrace();
                                                 return null;
                                             }
                            
                                             //Actually send the packet
                                             try {
                                                 rtpSock.send(packet);
                                                 //Debug
                                                 if(this.debugAppIntf != null) {
                                                     this.debugAppIntf.packetSent(0, (InetSocketAddress) packet.getSocketAddress(), 
                                                             new String("Sent unicast RTP packet of size " + packet.getLength() + 
                                                                     " to " + packet.getSocketAddress().toString() + " via " 
                                                                     + rtpSock.getLocalSocketAddress().toString()));
                                                 }
                                             } catch (Exception e) {
                                                 System.out.println("RTPSession.sendData() unicast failed.");
                                                 e.printStackTrace();
                                                 return null;
                                             }
                                         }
                                     }
                            
                                     //Update our stats
                                     this.sentPktCount++;
                                     this.sentOctetCount++;
                            
                                     logger.info("<- RTPSession.sendData(byte[])", pkt.getSeqNumber());
                                 }
                            
                                 return ret;
                             }
    
    
	/********************************************* Feedback message stuff ***************************************/
    protected boolean rtcpAVPFIntfIsNull(){return null == this.rtcpAVPFIntf;}

  



 

 
}
