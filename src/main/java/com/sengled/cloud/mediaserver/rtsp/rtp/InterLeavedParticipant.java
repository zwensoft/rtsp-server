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

import jlibrtp.AbstractParticipant;

/**
 * A participant represents a peer in an RTPSession. Based on the information stored on 
 * these objects, packets are processed and statistics generated for RTCP.
 */
public class InterLeavedParticipant extends AbstractParticipant {
    private InterLeavedRTPSession session;
    
    
    public InterLeavedParticipant(InterLeavedRTPSession rtpSession, long ssrc) {
        this.session = rtpSession;
        this.ssrc = ssrc;
    }


    public int rtpChannel() {
        return session.rtpChannel();
    }
    
    public void rtpChannel(int rtpChannel) {
        session.rtpChannel(rtpChannel);
    }
    
}
