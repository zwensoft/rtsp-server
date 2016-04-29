package jlibrtp;

import java.util.Enumeration;
import java.util.Iterator;
import java.util.Random;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import jlibrtp.AbstractParticipantDatabase.ParticipantDatabaseFactory;
import jlibrtp.udp.Participant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



public abstract class AbstractRTPSession {
    private static final Logger logger = LoggerFactory.getLogger(AbstractRTPSession.class);

    /**
      * The debug level is final to avoid compilation of if-statements.</br>
      * 0 provides no debugging information, 20 provides everything </br>
      * Debug output is written to System.out</br>
      * Debug level for RTP related things.
      */
    public static final int rtpDebugLevel = 11;
    /**
      * The debug level is final to avoid compilation of if-statements.</br>
      * 0 provides no debugging information, 20 provides everything </br>
      * Debug output is written to System.out</br>
      * Debug level for RTCP related things.
      */
    public static final int rtcpDebugLevel = 0;
    
    /** Handle to application interface for RTCP (optional) */
    protected RTCPAppIntf rtcpAppIntf = null;
    /** Handle to application interface for AVPF, RFC 4585 (optional) */
    protected RTCPAVPFIntf rtcpAVPFIntf = null;
    /** Handle to application interface for debugging */
    protected DebugAppIntf debugAppIntf = null;
    
    
    
    /** Whether this session is a multicast session or not */
    protected boolean mcSession = false;
    /** Current payload type, can be changed by application */
    protected int payloadType = 0;
    /** SSRC of this session */
    protected long ssrc;
    /** The last timestamp when we sent something */
    protected long lastTimestamp = 0;
    /** Current sequence number */
    protected int seqNum = 0;
    /** Number of packets sent by this session */
    public long sentPktCount = 0;
    /** Number of octets sent by this session */
    public long sentOctetCount = 0;
    /** The random seed */
    protected Random random = null;
    /** Session bandwidth in BYTES per second */
    protected int bandwidth = 8000;
    /** By default we do not return packets from strangers in unicast mode */
    protected boolean naiveReception = false;
    /** Should the library attempt frame reconstruction? */
    protected boolean frameReconstruction = true;
    /** Maximum number of packets used for reordering */
    protected int pktBufBehavior = 3;
    /** Participant database */
    final protected AbstractParticipantDatabase partDb;
    /** Handle to application interface for RTP */
    protected RTPAppIntf appIntf = null;
    /** The RTCP session associated with this RTP Session */
    protected AbstractRTCPSession rtcpSession = null;
    /** Lock to protect the packet buffers */
    public final Lock pktBufLock = new ReentrantLock();
    /** Condition variable, to tell the  */
    public final Condition pktBufDataReady = pktBufLock.newCondition();
    /** Enough is enough, set to true when you want to quit. */
    public boolean endSession = false;
    /** Only one registered application, please */
    protected boolean registered = false;
    /** We're busy resolving a SSRC conflict, please try again later */
    protected boolean conflict = false;
    /** Number of conflicts observed, exessive number suggests loop in network */
    protected int conflictCount = 0;
    /** SDES CNAME */
    protected String cname = null;
    /** SDES The participant's real name */
    public String name = null;
    /** SDES The participant's email */
    public String email = null;
    /** SDES The participant's phone number */
    public String phone = null;
    /** SDES The participant's location*/
    public String loc = null;
    /** SDES The tool the participants is using */
    public String tool = null;
    /** SDES A note */
    public String note = null;
    /** SDES A priv string, loosely defined */
    public String priv = null;
    protected int rtcpMode = 0;
    protected int fbEarlyThreshold = -1;
    protected int fbRegularThreshold = -1;
    protected int minInterval = 5000;
    protected int fbMaxDelay = 1000;
    protected int rtcpBandwidth = -1;

    public AbstractRTPSession(ParticipantDatabaseFactory factory) {
        partDb = factory.newInstance(this);
    }

    public boolean mcSession() {
        return mcSession;
    }

    /**
      * Send RTCP App packet to receiver specified by ssrc
      * 
      * 
      * 
      * Return values:
      *  0 okay
      * -1 no RTCP session established
      * -2 name is not byte[4];
      * -3 data is not byte[x], where x = 4*y for syme y
      * -4 type is not a 5 bit unsigned integer
      * 
      * Note that a return value of 0 does not guarantee delivery.
      * The participant must also exist in the participant database,
      * otherwise the message will eventually be deleted.
      * 
      * @param ssrc of the participant you want to reach
      * @param type the RTCP App packet subtype, default 0
      * @param name the ASCII (in byte[4]) representation
      * @param data the data itself 
      * @return 0 if okay, negative value otherwise (see above)
      */
    public int sendRTCPAppPacket(long ssrc,
                                     int type,
                                     byte[] name,
                                     byte[] data) {
                                    	 if(this.rtcpSession == null)
                                    		 return -1;
                                    	 
                                    	 if(name.length != 4)
                                    		 return -2;
                                    	 
                                    	 if(data.length % 4 != 0)
                                    		 return -3;
                                    	 
                                    	 if(type > 63 || type < 0 )
                                    		 return -4;
                                    	
                                    	RtcpPktAPP pkt = new RtcpPktAPP(ssrc, type, name, data);
                                    	this.rtcpSession.addToAppQueue(ssrc, pkt);
                                    	
                                    	return 0;
                                     }

    /**
      * Add a participant object to the participant database.
      * 
      * If packets have already been received from this user, we will try to update the automatically inserted participant with the information provided here.
      *
      * @param p A participant.
      */
    public int addParticipant(Participant p) {
    	//For now we make all participants added this way persistent
    	p.unexpected = false;
    	return this.partDb.addParticipant(0, p);
    }

    /**
      * Remove a participant from the database. All buffered packets will be destroyed.
      *
      * @param p A participant.
      */
    public void removeParticipant(AbstractParticipant p) {
    	partDb.removeParticipant(p);
     }

    public Iterator<AbstractParticipant> getUnicastReceivers() {
    	 return partDb.getUnicastReceivers();
     }

    public Enumeration<AbstractParticipant> getParticipants() {
    	 return partDb.getParticipants();
     }

    /**
     * End the RTP Session. This will halt all threads and send bye-messages to other participants.
     * 
     * RTCP related threads may require several seconds to wake up and terminate.
     */
    public abstract void endSession();


    /**
      * Check whether this session is ending.
      * 
      * @return true if session and associated threads are terminating.
      */
    boolean isEnding() {
    	return this.endSession;
    }

    /**
     * Overrides CNAME, used for outgoing RTCP packets.
     * 
     * @param cname a string, e.g. username@hostname. Must be unique for session.
     */
    public void CNAME(String cname) {
    	this.cname = cname;
    }

    /**
     * Get the current CNAME, used for outgoing SDES packets
     */
    public String CNAME() {
    	return this.cname;
    }


    protected abstract void generateCNAME();
    

    /**
     * Update the payload type used for the session. It is represented as a 7 bit integer, whose meaning must be negotiated elsewhere (see IETF RFCs <a href="http://www.ietf.org/rfc/rfc3550.txt">3550</a> and <a href="http://www.ietf.org/rfc/rfc3550.txt">3551</a>)
     * 
     * @param payloadT an integer representing the payload type of any subsequent packets that are sent.
     */
    public int payloadType(int payloadT) {
    	if(payloadT > 128 || payloadT < 0) {
    		return -1;
    	} else {
    		this.payloadType = payloadT;
    		return this.payloadType;
    	}
    }

    /**
     * Get the payload type that is currently used for outgoing RTP packets.
     * 
     * @return payload type as integer
     */
    public int payloadType() {
    	return this.payloadType;
    }

    /**
     * Should packets from unknown participants be returned to the application? This can be dangerous.
     * 
     * @param doAccept packets from participants not added by the application.
     */
    public void naivePktReception(boolean doAccept) {
    	naiveReception = doAccept;
    }

    /**
     * Are packets from unknown participants returned to the application?
     * 
     * @return whether we accept packets from participants not added by the application.
     */
    public boolean naivePktReception() {
    	return naiveReception;
    }

    /**
     * Set the number of RTP packets that should be buffered when a packet is
     * missing or received out of order. Setting this number high increases
     * the chance of correctly reordering packets, but increases latency when
     * a packet is dropped by the network.
     * 
     * Packets that arrive in order are not affected, they are passed straight
     * to the application.
     * 
     * The maximum delay is numberofPackets * packet rate , where the packet rate
     * depends on the codec and profile used by the sender.
     * 
     * Valid values:
     *  >0 - The maximum number of packets (based on RTP Timestamp) that may accumulate
     *  0 - All valid packets received in order will be given to the application
     * -1 - All valid packets will be given to the application
     * 
     * @param behavior the be
     * @return the behavior set, unchanged in the case of a erroneous value
     */
    public int packetBufferBehavior(int behavior) {
    	if(behavior > -2) {
    		this.pktBufBehavior = behavior; 
    		// Signal the thread that pushes data to application
    		this.pktBufLock.lock();
    		try { this.pktBufDataReady.signalAll(); } finally {
    			this.pktBufLock.unlock();
    		}
    		return this.pktBufBehavior;
    	} else {
    		return this.pktBufBehavior;
    	}
    }

    /**
     * The number of RTP packets that should be buffered when a packet is
     * missing or received out of order. A high number  increases the chance 
     * of correctly reordering packets, but increases latency when a packet is 
     * dropped by the network.
     * 
     * A negative value disables the buffering, out of order packets will simply be dropped.
     * 
     * @return the maximum number of packets that can accumulate before the first is returned
     */
    public int packetBufferBehavior() {
    	return this.pktBufBehavior;
    }

    /**
     * Enable / disable frame reconstruction in the packet buffers.
     * This is only relevant if getPacketBufferBehavior > 0;
     * 
     * Default is true.
     */
    public void frameReconstruction(boolean toggle) {
    	this.frameReconstruction = toggle;
    }

    /**
     * Whether the packet buffer will attempt to reconstruct
     * packet automatically.  
     * 
     * @return the status
     */
    public boolean frameReconstruction() {
    	return this.frameReconstruction;
    }

    /**
     * The bandwidth currently allocated to the session,
     * in bytes per second. The default is 8000.
     * 
     * This value is not enforced and currently only
     * used to calculate the RTCP interval to ensure the
     * control messages do not exceed 5% of the total bandwidth
     * described here.
     * 
     * Since the actual value may change a conservative
     * estimate should be used to avoid RTCP flooding.
     * 
     * see rtcpBandwidth(void)
     * 
     * @return current bandwidth setting
     */
    public int sessionBandwidth() {
    	return this.bandwidth;
    }

    /**
     * Set the bandwidth of the session.
     * 
     * See sessionBandwidth(void) for details. 
     * 
     * @param bandwidth the new value requested, in bytes per second
     * @return the actual value set
     */
    public int sessionBandwidth(int bandwidth) {
    	if(bandwidth < 1) {
    		this.bandwidth = 8000;
    	} else {
    		this.bandwidth = bandwidth;
    	}
    	return this.bandwidth;
    }

    /**
     * RFC 3550 dictates that 5% of the total bandwidth,
     * as set by sessionBandwidth, should be dedicated
     * to RTCP traffic. This 
     * 
     * This should normally not be done, but is permissible in 
     * conjunction with feedback (RFC 4585) and possibly
     * other profiles. 
     * 
     * Also see sessionBandwidth(void)
     * 
     * @return current RTCP bandwidth setting, -1 means not in use
     */
    public int rtcpBandwidth() {
    	return this.rtcpBandwidth;
    }

    /**
     * Set the RTCP bandwidth, see rtcpBandwidth(void) for details. 
     * 
     * This function must be
     * 
     * @param bandwidth the new value requested, in bytes per second or -1 to disable
     * @return the actual value set
     */
    public int rtcpBandwidth(int bandwidth) {
    	if(bandwidth < -1) {
    		this.rtcpBandwidth = -1;
    	} else {
    		this.rtcpBandwidth = bandwidth;
    	}
    	return this.rtcpBandwidth;
    }

    /**
     * Adds a Picture Loss Indication to the feedback queue
     * 
     * @param ssrcMediaSource
     * @return 0 if packet was queued, -1 if no feedback support, 1 if redundant
     */
    public int fbPictureLossIndication(long ssrcMediaSource) {
    	int ret = 0;
    	
    	if(rtcpAVPFIntfIsNull())
            return -1;

    	RtcpPktPSFB pkt = new RtcpPktPSFB(this.ssrc, ssrcMediaSource);
    	pkt.makePictureLossIndication();
    	ret = this.rtcpSession.addToFbQueue(ssrcMediaSource, pkt);
    	if(ret == 0)
    		this.rtcpSession.wakeSenderThread(ssrcMediaSource);
    	return ret; 
    }

    /**
     * Adds a Slice Loss Indication to the feedback queue
     * 
     * @param ssrcMediaSource
     * @param sliFirst macroblock (MB) address of the first lost macroblock
     * @param sliNumber number of lost macroblocks
     * @param sliPictureId six least significant bits of the codec-specific identif
     * @return 0 if packet was queued, -1 if no feedback support, 1 if redundant
     */
    public int fbSlicLossIndication(long ssrcMediaSource,
                                        int[] sliFirst,
                                        int[] sliNumber,
                                        int[] sliPictureId) {
                                        	int ret = 0;
                                        	if(rtcpAVPFIntfIsNull())
                                        		return -1;
                                        	
                                        	RtcpPktPSFB pkt = new RtcpPktPSFB(this.ssrc, ssrcMediaSource);
                                        	pkt.makeSliceLossIndication(sliFirst, sliNumber, sliPictureId);
                                        	
                                        	ret = this.rtcpSession.addToFbQueue(ssrcMediaSource, pkt);
                                        	if(ret == 0)
                                        		this.rtcpSession.wakeSenderThread(ssrcMediaSource);
                                        	return ret; 
                                        }


    protected boolean rtcpAVPFIntfIsNull(){return false;}

    /**
     * Adds a Reference Picture Selection Indication to the feedback queue
     * 
     * @param ssrcMediaSource
     * @param bitPadding number of padded bits at end of bitString
     * @param payloadType RTP payload type for codec
     * @param bitString RPSI information as natively defined by the video codec
     * @return 0 if packet was queued, -1 if no feedback support, 1 if redundant
     */
    public int fbRefPictureSelIndic(long ssrcMediaSource,
                                        int bitPadding,
                                        int payloadType,
                                        byte[] bitString) {
                                        	int ret = 0;
                                        	
                                        	if(rtcpAVPFIntfIsNull())
                                        		return -1;
                                        	
                                        	RtcpPktPSFB pkt = new RtcpPktPSFB(this.ssrc, ssrcMediaSource);
                                        	pkt.makeRefPictureSelIndic(bitPadding, payloadType, bitString);
                                        	ret = this.rtcpSession.addToFbQueue(ssrcMediaSource, pkt);
                                        	if(ret == 0)
                                        		this.rtcpSession.wakeSenderThread(ssrcMediaSource);
                                        	return ret; 
                                        }

    /**
     * Adds a Picture Loss Indication to the feedback queue
     * 
     * @param ssrcMediaSource
     * @param bitString the original application message
     * @return 0 if packet was queued, -1 if no feedback support, 1 if redundant
     */
    public int fbAppLayerFeedback(long ssrcMediaSource,
                                      byte[] bitString) {
                                    	int ret = 0;
                                    	
                                    	if(rtcpAVPFIntfIsNull())
                                    		return -1;
                                    	
                                    	RtcpPktPSFB pkt = new RtcpPktPSFB(this.ssrc, ssrcMediaSource);
                                    	pkt.makeAppLayerFeedback(bitString);
                                    	ret = this.rtcpSession.addToFbQueue(ssrcMediaSource, pkt);
                                    	if(ret == 0)
                                    		this.rtcpSession.wakeSenderThread(ssrcMediaSource);
                                    	return ret; 
                                    }

    /**
     * Adds a RTP Feedback packet to the feedback queue.
     * 
     * These are mostly used for NACKs.
     * 
     * @param ssrcMediaSource
     * @param FMT the Feedback Message Subtype
     * @param PID RTP sequence numbers of lost packets
     * @param BLP bitmask of following lost packets, shared index with PID 
     * @return 0 if packet was queued, -1 if no feedback support, 1 if redundant
     */
    public int fbPictureLossIndication(long ssrcMediaSource,
                                           int FMT,
                                           int[] PID,
                                           int[] BLP) {
                                        	int ret = 0;
                                        	
                                        	
                                        	RtcpPktRTPFB pkt = new RtcpPktRTPFB(this.ssrc, ssrcMediaSource, FMT, PID, BLP);
                                        	ret = this.rtcpSession.addToFbQueue(ssrcMediaSource, pkt);
                                        	if(ret == 0)
                                        		this.rtcpSession.wakeSenderThread(ssrcMediaSource);
                                        	return ret; 
                                        }

    /**
     * Fetches the next sequence number for RTP packets.
     * @return the next sequence number 
     */
    protected int getNextSeqNum() {
    	seqNum++;
    	// 16 bit number
    	if(seqNum > 65536) { 
    		seqNum = 0;
    	}
    	return seqNum;
    }

    /** 
     * Initializes a random variable
     *
     */
    private void createRandom() {
    	this.random = new Random(System.currentTimeMillis() + Thread.currentThread().getId() 
    			- Thread.currentThread().hashCode() + this.cname.hashCode());
    }

    /** 
     * Generates a random sequence number
     */
    protected void generateSeqNum() {
    	if(this.random == null)
    		createRandom();
    	
    	seqNum = this.random.nextInt();
    	if(seqNum < 0)
    		seqNum = -seqNum;
    	while(seqNum > 65535) {
    		seqNum = seqNum / 10;
    	}
    }

    /**
     * Generates a random SSRC
     */
    protected void generateSsrc() {
    	if(this.random == null)
    		createRandom();
    	
    	// Set an SSRC
    	this.ssrc = this.random.nextInt();
    	if(this.ssrc < 0) {
    		this.ssrc = this.ssrc * -1;
    	}	
    }

    /**
     * Resolve an SSRC conflict.
     * 
     * Also increments the SSRC conflict counter, after 5 conflicts
     * it is assumed there is a loop somewhere and the session will
     * terminate. 
     *
     */
    public void resolveSsrcConflict() {
    	System.out.println("!!!!!!! Beginning SSRC conflict resolution !!!!!!!!!");
    	this.conflictCount++;
    	
    	if(this.conflictCount < 5) {
    		//Don't send any more regular packets out until we have this sorted out.
    		this.conflict = true;
    	
    		//Send byes
    		rtcpSession.sendByes();
    	
    		//Calculate the next delay
    		rtcpSession.calculateDelay();
    		
    		//Generate a new Ssrc for ourselves
    		generateSsrc();
    		
    		//Get the SDES packets out faster
    		rtcpSession.initial = true;
    		
    		this.conflict = false;
    		System.out.println("SSRC conflict resolution complete");
    		
    	} else {
    		System.out.println("Too many conflicts. There is probably a loop in the network.");
    		this.endSession();
    	}
    }

    public RTCPAppIntf rtcpAppIntf() {
        return rtcpAppIntf;
    }
    public RTPAppIntf appIntf() {
        return appIntf;
    }
    
    public DebugAppIntf debugAppIntf() {
        return debugAppIntf;
    }

    public AbstractParticipantDatabase  partDb() {
        return partDb;
    }
    
    public long ssrc() {
        return ssrc;
    }
    
    public boolean conflict() {
        return conflict;
    }
}
