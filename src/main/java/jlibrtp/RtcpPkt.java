package jlibrtp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



public class RtcpPkt {
    static final Logger logger = LoggerFactory.getLogger(RtcpPkt.class);
    
    /** Whether a problem has been encountered during parsing */
    public int problem = 0;
    /** The version, always 2, 2 bits */
    protected int version = 2;
    /** Padding , 1 bit */
    protected int padding = 0;
    /** Number of items, e.g. receiver report blocks. Usage may vary. 5 bits */
    protected int itemCount = 0;
    /** The type of RTCP packet, 8 bits */
    protected int packetType = -1;
    /** The length of the RTCP packet, in 32 bit blocks minus 1. 16 bits*/
    protected int length = -1;
    /** The ssrc that sent this, usually dictated by RTP Session */
    protected long ssrc = -1;
    /** Contains the actual data (eventually) */
    public byte[] rawPkt = null;
    /** Only used for feedback messages: Time message was generated */
    protected long time = -1;
    /** Only used for feedback message: Whether this packet was received */
    protected boolean received = false;

    public RtcpPkt() {
        super();
    }

    /**
     * Parses the common header of an RTCP packet
     * 
     * @param start where in this.rawPkt the headers start
     * @return true if parsing succeeded and header cheks 
     */
    protected boolean parseHeaders(int start) {
    	version = ((rawPkt[start+0] & 0xC0) >>> 6);
    	padding = ((rawPkt[start+0] & 0x20) >>> 5);
    	itemCount = (rawPkt[start+0] & 0x1F);
    	packetType = (int) rawPkt[start+1];
    	if(packetType < 0) {
    		packetType += 256;
    	}
    	length = StaticProcs.bytesToUIntInt(rawPkt, start+2);
    	
    	if (logger.isDebugEnabled()) {
    	logger.debug(" <-> RtcpPkt.parseHeaders() version:"+version+" padding:"+padding+" itemCount:"+itemCount
    			+" packetType:"+packetType+" length:"+length);
    	}
    	
    	if(packetType > 207 || packetType < 200) 
    		System.out.println("RtcpPkt.parseHeaders problem discovered, packetType " + packetType);
    	
    	if(version == 2 && length < 65536) {
    		return true;
    	} else {
    		System.out.println("RtcpPkt.parseHeaders() failed header checks, check size and version");
    		this.problem = -1;
    		return false;
    	}
    }

    /**
     * Writes the common header of RTCP packets. 
     * The values should be filled in when the packet is initiliazed and this function
     * called at the very end of .encode()
     */
    protected void writeHeaders() {
    	byte aByte = 0;
    	aByte |=(version << 6);
    	aByte |=(padding << 5);
    	aByte |=(itemCount);
    	rawPkt[0] = aByte;
    	aByte = 0;
    	aByte |= packetType;
    	rawPkt[1] = aByte;
    	if(rawPkt.length % 4 != 0)
    		System.out.println("!!!! RtcpPkt.writeHeaders() rawPkt was not a multiple of 32 bits / 4 octets!");
    	byte[] someBytes = StaticProcs.uIntIntToByteWord((rawPkt.length / 4) - 1);
    	rawPkt[2] = someBytes[0];
    	rawPkt[3] = someBytes[1];
    }

    /**
     * This is just a dummy to make Eclipse complain less.
     */
    public void encode() {
    	System.out.println("RtcpPkt.encode() should never be invoked!! " + this.packetType);
    }

    public int itemCount() {
        return itemCount;
    }

    public long ssrc() {
        return ssrc;
    }
    
    public int packetType() {
        return packetType;
    }


}
