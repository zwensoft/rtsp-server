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
package com.sengled.cloud.mediaserver.rtsp.rtp;

import java.util.Enumeration;
import java.util.Iterator;

import jlibrtp.AbstractParticipant;
import jlibrtp.AbstractParticipantDatabase;
import jlibrtp.AbstractRTPSession;

/**
 * The participant database maintains three hashtables with participants.
 * 
 * The key issue is to be fast for operations that happen every time an
 * RTP packet is sent or received. We allow linear searching in cases 
 * where we need to update participants with information.
 * 
 * The keying is therefore usually the SSRC. In cases where we have the
 * cname, but no SSRC is known (no SDES packet has been received), a
 * simple hash i calculated based on the CNAME. The RTCP code should,
 * when receiving SDES packets, check whether the participant is known
 * and update the copy in this database with SSRC if needed.
 * 
 * @author Arne Kepp
 */
public class InterLeavedParticipantDatabase extends AbstractParticipantDatabase {

    public static final ParticipantDatabaseFactory FACTORY = new ParticipantDatabaseFactory() {
        
        @Override
        public AbstractParticipantDatabase newInstance(AbstractRTPSession rtpSession) {
            return new InterLeavedParticipantDatabase((InterLeavedRTPSession)rtpSession);
        }
    };
    
	
	public InterLeavedParticipantDatabase(InterLeavedRTPSession parent) {
        super(parent);
    }

    /**
	 * 
	 * @param cameFrom 0: Application, 1: RTP packet, 2: RTCP
	 * @param p the participant
	 * @return 0 if okay, -1 if not 
	 */
	@Override
    public int addParticipant(int cameFrom, AbstractParticipant p) {
	    InterLeavedParticipant newPart = (InterLeavedParticipant)p;
	    
        if(cameFrom == 0) {
            //Check whether there is a match in the ssrcTable
            boolean notDone = true;
            
            Enumeration<AbstractParticipant> enu = this.ssrcTable.elements();
            while(notDone && enu.hasMoreElements()) {
                InterLeavedParticipant part = (InterLeavedParticipant)enu.nextElement();
                if(part.unexpected() && part.rtpChannel() == newPart.rtpChannel()) {
                    
                    part.rtpChannel(newPart.rtpChannel());
                    part.unexpected(false);

                    //Report the match back to the application
                    AbstractParticipant[] partArray = {part};
                    this.rtpSession.appIntf().userEvent(5, partArray);
                    
                    notDone = false;
                    p = part;
                }
            }

            //Add to the table of people that we send packets to
            this.receivers.add(p);
            return 0;
            
        } else {
            //Check whether there's a match in the receivers table
            boolean notDone = true;
            //System.out.println("GOT " + p.cname);
            Iterator<AbstractParticipant> iter = this.receivers.iterator();
            
            while(notDone && iter.hasNext()) {
                InterLeavedParticipant part = (InterLeavedParticipant)iter.next();
                
                //System.out.println(part.rtpAddress.getAddress().toString()
                //      + " " + part.rtcpAddress.getAddress().toString() 
                //      + " " + p.rtpReceivedFromAddress.getAddress().toString()
                //      + " " + p.rtcpReceivedFromAddress.getAddress().toString());
                
                if((cameFrom == 1 && part.rtpChannel() == newPart.rtpChannel())
                    || (cameFrom == 2 && part.rtpChannel() == newPart.rtpChannel())) {

                    part.rtpChannel(newPart.rtpChannel());
                    
                    // Move information
                    part.ssrc(p.ssrc());
                    part.cname = p.cname;
                    part.name = p.name;
                    part.loc = p.loc;
                    part.phone = p.phone;
                    part.email = p.email;
                    part.note = p.note;
                    part.tool = p.tool;
                    part.priv = p.priv;
                    
                    this.ssrcTable.put(part.ssrc(), part);
                    
                    //Report the match back to the application
                    AbstractParticipant[] partArray = {part};
                    this.rtpSession.appIntf().userEvent(5, partArray);
                    return 0;
                }
            }
            
            // No match? ok
            this.ssrcTable.put(p.ssrc(), p);              
            return 0;
        }
	}
}
