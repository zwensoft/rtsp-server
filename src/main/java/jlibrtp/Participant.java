package jlibrtp;

import java.sql.Timestamp;



public class Participant {

    /** Whether the participant is unexpected, e.g. arrived through unicast with SDES */
    protected boolean unexpected = false;
    /** SSRC of participant */
    protected long ssrc = -1;
    /** SDES CNAME */
    public String cname = null;
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
    /** RR First sequence number */
    public int firstSeqNumber = -1;
    /** RR Last sequence number */
    public int lastSeqNumber = 0;
    /** RR Number of times sequence number has rolled over */
    protected long seqRollOverCount = 0;
    /** RR Number of packets received */
    public long receivedPkts = 0;
    /** RR Number of octets received */
    protected long receivedOctets = 0;
    /** RR Number of packets received since last SR */
    protected int receivedSinceLastSR = 0;
    /** RR Sequence number associated with last SR */
    protected int lastSRRseqNumber = 0;
    /** RR Interarrival jitter */
    protected double interArrivalJitter = -1.0;
    /** RR Last received RTP Timestamp */
    protected long lastRtpTimestamp = 0;
    /** RR Middle 32 bits of the NTP timestamp in the last SR */
    public long timeStampLSR = 0;
    /** RR The time when we actually got the last SR */
    public long timeReceivedLSR = 0;
    /** Gradient where UNIX timestamp = ntpGradient*RTPTimestamp * ntpOffset */
    public double ntpGradient = -1;
    /** Offset where UNIX timestamp = ntpGradient*RTPTimestamp * ntpOffset */
    public long ntpOffset = -1;
    /** Last NTP received in SR packet, MSB */
    public long lastNtpTs1 = 0;
    /** Last NTP received in SR packet, LSB */
    public long lastNtpTs2 = 0;
    /** RTP Timestamp in last SR packet */
    public long lastSRRtpTs = 0;
    /** UNIX time when a BYE was received from this participant, for pruning */
    public long timestampBYE = -1;
    /** Store the packets received from this participant */
    protected PktBuffer pktBuffer = null;
    /** UNIX time of last RTP packet, to check whether this participant has sent anything recently */
    public long lastRtpPkt = -1;
    /** UNIX time of last RTCP packet, to check whether this participant has sent anything recently */
    public long lastRtcpPkt = -1;
    /** UNIX time this participant was added by application, to check whether we ever heard back */
    protected long addedByApp = -1;
    /** UNIX time of last time we sent an RR to this user */
    public long lastRtcpRRPkt = -1;
    /** Unix time of second to last time we sent and RR to this user */
    public long secondLastRtcpRRPkt = -1;

    public Participant() {
        super();
    }

    public Participant(long SSRC) {
        ssrc = SSRC;
        unexpected = true;
    }

    /**
     * CNAME registered for this participant.
     * 
     * @return the cname
     */
    public String getCNAME() {
    	return cname;
    }

    /**
     * NAME registered for this participant.
     * 
     * @return the name
     */
    public String getNAME() {
    	return name;
    }

    /**
     * EMAIL registered for this participant.
     * 
     * @return the email address
     */
    public String getEmail() {
    	return email;
    }

    /**
     * PHONE registered for this participant.
     * 
     * @return the phone number
     */
    public String getPhone() {
    	return phone;
    }

    /**
     * LOCATION registered for this participant.
     * 
     * @return the location
     */
    public String getLocation() {
    	return loc;
    }

    /**
     * NOTE registered for this participant.
     * 
     * @return the note
     */
    public String getNote() {
    	return note;
    }

    /**
     * PRIVATE something registered for this participant.
     * 
     * @return the private-string
     */
    public String getPriv() {
    	return priv;
    }

    /**
     * TOOL something registered for this participant.
     * 
     * @return the tool
     */
    public String getTool() {
    	return tool;
    }

    /**
     * SSRC for participant, determined through RTCP SDES
     * 
     * @return SSRC (32 bit unsigned integer as long)
     */
    public long getSSRC() {
    	return this.ssrc;
    }

    /**
     * Updates the participant with information for receiver reports.
     * 
     * @param packetLength to keep track of received octets
     * @param pkt the most recently received packet
     */
    public void updateRRStats(int packetLength, IRtpPkt pkt) {
        int curSeqNum = pkt.getSeqNumber();

        if (firstSeqNumber < 0) {
            firstSeqNumber = curSeqNum;
        }

        receivedOctets += packetLength;
        receivedSinceLastSR++;
        receivedPkts++;

        long curTime = System.currentTimeMillis();

        if (this.lastSeqNumber < curSeqNum) {
            // In-line packet, best thing you could hope for
            this.lastSeqNumber = curSeqNum;

        } else if (this.lastSeqNumber - this.lastSeqNumber < -100) {
            // Sequence counter rolled over
            this.lastSeqNumber = curSeqNum;
            seqRollOverCount++;

        } else {
            // This was probably a duplicate or a late arrival.
        }

        // Calculate jitter
        if (this.lastRtpPkt > 0) {

            long D = (pkt.getTimestamp() - curTime) - (this.lastRtpTimestamp - this.lastRtpPkt);
            if (D < 0)
                D = (-1) * D;

            this.interArrivalJitter += ((double) D - this.interArrivalJitter) / 16.0;
        }

        lastRtpPkt = curTime;
        lastRtpTimestamp = pkt.getTimestamp();
    }

    /**
     * Calculates the extended highest sequence received by adding 
     * the last sequence number to 65536 times the number of times 
     * the sequence counter has rolled over.
     * 
     * @return extended highest sequence
     */
    protected long getExtHighSeqRecv() {
    	return (65536*seqRollOverCount + lastSeqNumber);
    }

    /**
     * Get the fraction of lost packets, calculated as described
     * in RFC 3550 as a fraction of 256.
     * 
     * @return the fraction of lost packets since last SR received
     */
    protected int getFractionLost() {
    	int expected = (lastSeqNumber - lastSRRseqNumber);
    	if(expected < 0)
    		expected = 65536 + expected;
                
    	int fraction = 256 * (expected - receivedSinceLastSR);
    	if(expected > 0) {
    		fraction = (fraction / expected);
    	} else {
    		fraction = 0;
    	}
    	
    	//Clear counters 
    	receivedSinceLastSR = 0;
    	lastSRRseqNumber = lastSeqNumber;
    	
    	return fraction;
    }

    /**
     * The total number of packets lost during the session.
     * 
     * Returns zero if loss is negative, i.e. duplicates have been received.
     * 
     * @return number of lost packets, or zero.
     */
    protected long getLostPktCount() {
    	long lost = (this.getExtHighSeqRecv() - this.firstSeqNumber) - receivedPkts;
    	
    	if(lost < 0)
    		lost = 0;
    	return lost;
    }

    /** 
     * 
     * @return the interArrivalJitter, calculated continuously
     */
    protected double getInterArrivalJitter() {
    	return this.interArrivalJitter;
    }

    /**
     * Set the timestamp for last sender report
     * 
     * @param ntp1 high order bits
     * @param ntp2 low order bits
     */
    public void setTimeStampLSR(long ntp1,
                                      long ntp2) {
                                    	// Use what we've got
                                    	byte[] high = StaticProcs.uIntLongToByteWord(ntp1);
                                    	byte[] low = StaticProcs.uIntLongToByteWord(ntp2);
                                    	low[3] = low[1];
                                    	low[2] = low[0];
                                    	low[1] = high[3];
                                    	low[0] = high[2];
                                    	
                                    	this.timeStampLSR = StaticProcs.bytesToUIntLong(low, 0);
                                    }

    /**
     * Calculate the delay between the last received sender report
     * and now.
     * 
     * @return the delay in units of 1/65.536ms
     */
    protected long delaySinceLastSR() {
    	if(this.timeReceivedLSR < 1) 
    		return 0;
    		
    	long delay = System.currentTimeMillis() - this.timeReceivedLSR;
    	
    	//Convert ms into 1/65536s = 1/65.536ms
    	return (long) ((double)delay * 65.536);
    }
    
    final public long ssrc() {
        return ssrc;
    }
    
    final public String cname() {
        return cname;
    }

    final public void unexpected(boolean unexpected) {
        this.unexpected = unexpected;
    }
    final public boolean unexpected() {
        return this.unexpected;
    }

    final public PktBuffer pktBuffer() {
        return pktBuffer;
    }

    final public void pktBuffer(PktBuffer pktBuffer) {
        this.pktBuffer = pktBuffer;
    }

    final public void ssrc(long ssrc) {
       this.ssrc = ssrc;
    }
    
    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append("{Participant");
        buf.append(", ssrc=").append(ssrc());
        buf.append(", ntpGradient=").append(ntpGradient);
        buf.append(", lastRtcpPkt=").append(new Timestamp(lastRtcpPkt));
        buf.append(", timeReceivedLSR=").append(new Timestamp(timeReceivedLSR));
        buf.append(", ntpOffset=").append(ntpOffset);
        buf.append(", lastNtpTs1=").append(lastNtpTs1);
        buf.append(", lastNtpTs2=").append(lastNtpTs2);
        buf.append(", lastSRRtpTs=").append(lastSRRtpTs);
        
        buf.append("}");
        return buf.toString();
    }
}
