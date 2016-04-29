package jlibrtp;



public class AbstractRtcpPktSDES extends AbstractRtcpPkt {

    /** Whether the RTP Session object should be inclduded */
    protected boolean reportSelf = true;
    /** The parent RTP Session object, holds participant database */
    protected AbstractRTPSession rtpSession = null;
    /** The participants to create SDES packets for */
    protected AbstractParticipant[] participants = null;

    public static interface ParticipantFactory {
        AbstractParticipant newInstance(long ssrc);
    }

    
    /**
     * Constructor to create a new SDES packet
     * 
     * TODO:
     * Currently the added participants are not actually encoded
     * because the library lacks some support for acting as mixer or
     * relay in other areas.
     * 
     * @param reportThisSession include information from RTPSession as a participant
     * @param rtpSession the session itself
     * @param additionalParticipants additional participants to include
     */
    protected AbstractRtcpPktSDES(boolean reportThisSession, AbstractRTPSession rtpSession, AbstractParticipant[] additionalParticipants) {
        super.packetType = 202;
        // Fetch all the right stuff from the database
        this.reportSelf = reportThisSession;
        this.participants = additionalParticipants;
        this.rtpSession = rtpSession; 
    }

    

    /**
     * Constructor that parses a received packet
     * 
     * @param aRawPkt the byte[] containing the packet
     * @param start where in the byte[] this packet starts
     * @param partDb the participant database
     */
    protected AbstractRtcpPktSDES(byte[] aRawPkt,int start, AbstractParticipantDatabase partDb, ParticipantFactory factory) {
        if(AbstractRTPSession.rtcpDebugLevel > 8) {
            System.out.println("  -> RtcpPktSDES(byte[], ParticipantDabase)");
        }
        rawPkt = aRawPkt;

        if(! super.parseHeaders(start) || packetType != 202 ) {
            if(AbstractRTPSession.rtpDebugLevel > 2) {
                System.out.println(" <-> RtcpPktSDES.parseHeaders() etc. problem");
            }
            super.problem = -202;
        } else {
            //System.out.println(" DECODE SIZE: " + super.length + " itemcount " + itemCount );
            
            int curPos = 4 + start;
            int curLength;
            int curType;
            long ssrc;
            boolean endReached = false;
            boolean newPart;
            this.participants = new AbstractParticipant[itemCount];
            
            // Loop over SSRC SDES chunks
            for(int i=0; i< itemCount; i++) {
                ssrc = StaticProcs.bytesToUIntLong(aRawPkt, curPos);
                AbstractParticipant part = partDb.getParticipant(ssrc);
                if(part == null) {
                    if(AbstractRTPSession.rtcpDebugLevel > 1) {
                        System.out.println("RtcpPktSDES(byte[], ParticipantDabase) adding new participant, ssrc:"+ssrc);
                    }
                    
                    part = factory.newInstance(ssrc);
                    newPart = true;
                } else {
                    newPart = false;
                }
                
                curPos += 4;
                            
                //System.out.println("PRE endReached " + endReached + " curPos: " + curPos + " length:" + this.length + (!endReached && (curPos/4) < this.length));
                
                while(!endReached && (curPos/4) <= this.length) {
                    //System.out.println("endReached " + endReached + " curPos: " + curPos + " length:" + this.length);
                    curType = (int) aRawPkt[curPos];
                    
                    if(curType == 0) {  
                        curPos += 4 - (curPos % 4);
                        endReached = true;
                    } else {
                        curLength  = (int) aRawPkt[curPos + 1];
                        //System.out.println("curPos:"+curPos+" curType:"+curType+" curLength:"+curLength+" read from:"+(curPos + 1));

                        if(curLength > 0) {
                            byte[] item = new byte[curLength];
                            //System.out.println("curPos:"+curPos+" arawPkt.length:"+aRawPkt.length+" curLength:"+curLength);
                            System.arraycopy(aRawPkt, curPos + 2, item, 0, curLength);
                            
                            switch(curType) {
                            case 1:  part.cname = new String(item); break;
                            case 2:  part.name = new String(item); break;
                            case 3:  part.email = new String(item); break;
                            case 4:  part.phone = new String(item); break;
                            case 5:  part.loc = new String(item); break;
                            case 6:  part.tool = new String(item); break;
                            case 7:  part.note = new String(item); break;
                            case 8:  part.priv = new String(item); break;
                            }
                            //System.out.println("TYPE " + curType + " value:" + new String(item) );
                            
                        } else {
                            switch(curType) {
                            case 1:  part.cname = null; break;
                            case 2:  part.name = null; break;
                            case 3:  part.email = null; break;
                            case 4:  part.phone = null; break;
                            case 5:  part.loc = null; break;
                            case 6:  part.tool = null; break;
                            case 7:  part.note = null; break;
                            case 8:  part.priv = null; break;
                            }
                            
                        }
                        curPos = curPos + curLength + 2;
                    }
                }
                
                // Save the participant
                this.participants[i] = part;
                if(newPart)
                    partDb.addParticipant(2,part);
                
                //System.out.println("HEPPPPPP " + participants[i].cname );
            }
        }
        if(AbstractRTPSession.rtcpDebugLevel > 8) {
            System.out.println("  <- RtcpPktSDES()");
        }
    }
    
    /**
     * Encode the packet into a byte[], saved in .rawPkt
     * 
     * CompRtcpPkt will call this automatically
     */
    public void encode() {	
    	byte[] temp = new byte[1450];
    	byte[] someBytes = StaticProcs.uIntLongToByteWord(this.rtpSession.ssrc);
    	System.arraycopy(someBytes, 0, temp, 4, 4);
    	int pos = 8;
    
    	String tmpString = null;
    	for(int i=1; i<9;i++) {			
    		switch(i) {
    			case 1:  tmpString = this.rtpSession.cname; break;
    			case 2:  tmpString = this.rtpSession.name; break;
    			case 3:  tmpString = this.rtpSession.email; break;
    			case 4:  tmpString = this.rtpSession.phone; break;
    			case 5:  tmpString = this.rtpSession.loc; break;
    			case 6:  tmpString = this.rtpSession.tool; break;
    			case 7:  tmpString = this.rtpSession.note; break;
    			case 8:  tmpString = this.rtpSession.priv; break;
    		}
    		
    		if(tmpString != null) {
    			someBytes = tmpString.getBytes();
    			temp[pos] = (byte) i;
    			temp[pos+1] = (byte) someBytes.length;
    			System.arraycopy(someBytes, 0, temp, pos + 2, someBytes.length);
    			//System.out.println("i: "+i+" pos:"+pos+" someBytes.length:"+someBytes.length);
    			pos = pos + someBytes.length + 2;
    			//if(i == 1 ) {
    			//	System.out.println("trueeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee" + tmpString);
    			//}
    		}
    	}
    	int leftover = pos % 4;
    	if(leftover == 1) {
    		temp[pos] = (byte) 0; 
    		temp[pos + 1] = (byte) 1; 
    		pos += 3;
    	} else if(leftover == 2) {
    		temp[pos] = (byte) 0; 
    		temp[pos + 1] = (byte) 0; 
    		pos += 2;
    	} else if(leftover == 3) {
    		temp[pos] = (byte) 0; 
    		temp[pos + 1] = (byte) 3; 
    		pos += 5;
    	}
    	
    	// TODO Here we ought to loop over participants, if we're doing SDES for other participants.
    	
    	this.rawPkt = new byte[pos];
    	itemCount = 1;
    	//This looks wrong, but appears to be fine..
    	System.arraycopy(temp, 0, this.rawPkt, 0, pos);
    	writeHeaders();
    }



    public AbstractParticipant[] participants() {
        return participants;
    }

    /**
     * Debug purposes only
     */
    public void debugPrint() {
        System.out.println("RtcpPktSDES.debugPrint() ");
        if(participants != null) {
            for(int i= 0; i<participants.length; i++) {
                AbstractParticipant part = participants[i];
                System.out.println("     part.ssrc: " + part.ssrc + "  part.cname: " + part.cname + " part.loc: " + part.loc);
            }
        } else {
            System.out.println("     nothing to report (only valid for received packets)");
        }
    }
    
    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append("{RtcpPktSDES");
        if(participants != null) {
            buf.append(", parts:[");
            for(int i= 0; i<participants.length; i++) {
                AbstractParticipant part = participants[i];
                buf.append("{");
                buf.append("ssrc=").append(part.ssrc);
                buf.append(", cname=").append(part.cname);
                buf.append(", loc=").append(part.loc);
                buf.append("}");
            }
            buf.append("]");
        } else {
            buf.append(", nothing to report (only valid for received packets)");
        }
        buf.append("}");
        return buf.toString();
    }
}

