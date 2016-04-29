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
package jlibrtp;

import jlibrtp.udp.RtcpPkt;

import org.apache.commons.lang.time.DateFormatUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sengled.cloud.mediaserver.rtsp.NtpTime;

/**
 * RTCP packets for Sender Reports 
 * 
 * @author Arne Kepp
 */
public class RtcpPktSR extends RtcpPkt {
	private static final Logger logger = LoggerFactory.getLogger(RtcpPktSR.class);
	
	/** NTP timestamp, MSB */
	public long ntpTs1 = -1; //32 bits
	/** NTP timestamp, LSB */
	public long ntpTs2 = -1; //32 bits
	/** RTP timestamp */
	public long rtpTs = -1; //32 bits
	/** Senders packet count */
	public long sendersPktCount = -1; //32 bits
	/** Senders octet count */
	public long sendersOctCount = -1; //32 bits
	/** RR packet with receiver reports that we can append */
	protected RtcpPktRR rReports = null;
	
	/**
	 * Constructor for a new Sender Report packet
	 * 
	 * @param ssrc the senders SSRC, presumably from RTPSession
	 * @param pktCount packets sent in this session
	 * @param octCount octets sent in this session
	 * @param rReports receiver reports, as RR packets, to be included in this packet
	 */
	public RtcpPktSR(long ssrc, long pktCount, long octCount, RtcpPktRR rReports) {
		// Fetch all the right stuff from the database
		super.ssrc = ssrc;
		super.packetType = 200;
		sendersPktCount = pktCount;
		sendersOctCount = octCount;
		this.rReports = rReports;
	}
	
	/**
	 * Constructor that parses a received packet
	 * 
	 * @param aRawPkt the raw packet
	 * @param start the position at which SR starts
	 * @param length used to determine number of included receiver reports
	 */
	public RtcpPktSR(byte[] aRawPkt, int start, int length) {
		super.rawPkt = aRawPkt;

		if(!super.parseHeaders(start) || packetType != 200 ) {
			logger.info(" <-> RtcpPktSR.parseHeaders() etc. problem: {} {} ", (!super.parseHeaders(start) ), packetType);
			super.problem = -200;
		} else {
			super.ssrc = StaticProcs.bytesToUIntLong(aRawPkt,4+start);
			if(length > 11)
				ntpTs1 = StaticProcs.bytesToUIntLong(aRawPkt,8+start);
			if(length > 15)
				ntpTs2 = StaticProcs.bytesToUIntLong(aRawPkt,12+start);
			if(length > 19)
				rtpTs = StaticProcs.bytesToUIntLong(aRawPkt,16+start);
			if(length > 23)
				sendersPktCount = StaticProcs.bytesToUIntLong(aRawPkt,20+start);
			if(length > 27)
				sendersOctCount = StaticProcs.bytesToUIntLong(aRawPkt,24+start);
			
			// RRs attached?
			if(itemCount > 0) {
				rReports = new RtcpPktRR(rawPkt,start,itemCount);
			}
		}
	}
	
	public long getNTPTime() {
	    return NtpTime.getNtpTime(ntpTs1, ntpTs2);
	}
	
	/**
	 * Encode the packet into a byte[], saved in .rawPkt
	 * 
	 * CompRtcpPkt will call this automatically
	 */
	public void encode() {		
	    if(this.rReports != null) {
            logger.info("  -> RtcpPktSR.encode() receptionReports.length: {}", this.rReports.length );
        } else {
            logger.info("  -> RtcpPktSR.encode() receptionReports: null");
        }
		
		if(this.rReports != null) {
			super.itemCount = this.rReports.reportees.length;
						
			byte[] tmp = this.rReports.encodeRR();
			super.rawPkt = new byte[tmp.length+28];
			//super.length = (super.rawPkt.length / 4) - 1;
			
			System.arraycopy(tmp, 0, super.rawPkt, 28, tmp.length);
			
		} else {
			super.itemCount = 0;
			super.rawPkt = new byte[28];
			//super.length = 6;
		}
		//Write the common header
		super.writeHeaders();
		
		
		//Write SR stuff
		byte[] someBytes;
		someBytes = StaticProcs.uIntLongToByteWord(super.ssrc);
		System.arraycopy(someBytes, 0, super.rawPkt, 4, 4);
		someBytes = StaticProcs.uIntLongToByteWord(ntpTs1);
		System.arraycopy(someBytes, 0, super.rawPkt, 8, 4);
		someBytes = StaticProcs.uIntLongToByteWord(ntpTs2);
		System.arraycopy(someBytes, 0, super.rawPkt, 12, 4);
		someBytes = StaticProcs.uIntLongToByteWord(rtpTs);
		System.arraycopy(someBytes, 0, super.rawPkt, 16, 4);
		someBytes = StaticProcs.uIntLongToByteWord(sendersPktCount);
		System.arraycopy(someBytes, 0, super.rawPkt, 20, 4);
		someBytes = StaticProcs.uIntLongToByteWord(sendersOctCount);
		System.arraycopy(someBytes, 0, super.rawPkt, 24, 4);
		
		logger.info("  <- {}", this);
	}

	

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append("{RtcpPktSR, ssrc=").append(ssrc);
        buf.append(", pktCount=").append(Long.toString(sendersPktCount));
        buf.append(", octetCount=").append(Long.toString(sendersOctCount));
        buf.append(", ntp=").append(DateFormatUtils.format(getNTPTime(), "yyyy-MM-dd HH:mm:ss.SSS"));
        buf.append(", rtpTs=").append(rtpTs);
        buf.append(", ntpTs1=").append(Long.toString(ntpTs1));
        buf.append(", ntpTs2=").append(Long.toString(ntpTs2));
        
        if (null != this.rReports) {
            buf.append(", Part of Sender Report: ").append(rReports);
        } else {
            buf.append(", No Receiver Reports.");
        }
        
        buf.append("}");
        return buf.toString();
    }
	

    public RtcpPktRR rReports() {
        return rReports;
    }
}
