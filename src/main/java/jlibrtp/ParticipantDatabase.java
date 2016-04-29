package jlibrtp;

import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentHashMap;



public abstract class ParticipantDatabase {

    public static interface ParticipantDatabaseFactory {
        ParticipantDatabase newInstance(RTPSession rtpSession);
    }
    
    /** The parent RTP Session */
    public RTPSession rtpSession = null;

    /**
     * Simple constructor
     * 
     * @param parent parent RTPSession
     */
    protected ParticipantDatabase(RTPSession parent) {
        rtpSession = parent;
    }

    /** 
     * A linked list to hold participants explicitly added by the application
     * In unicast mode this is the list used for RTP and RTCP transmission, 
     * in multicast it should not be in use. 
     */
    protected LinkedList<Participant> receivers = new LinkedList<Participant>();
    /** 
     * The hashtable holds participants added through received RTP and RTCP packets,
     * as well as participants that have been linked to an SSRC by ip address (in unicast mode).
     */
    protected ConcurrentHashMap<Long, Participant> ssrcTable = new ConcurrentHashMap<Long,Participant>();

    public ParticipantDatabase() {
        super();
    }


    /**
     * 
     * @param cameFrom 0: Application, 1: RTP packet, 2: RTCP
     * @param p the participant
     * @return 0 if okay, -1 if not 
     */
    public abstract int addParticipant(int cameFrom,
                                              Participant p);
    
    /**
     * Remove a participant from all tables
     * 
     * @param p the participant to be removed
     */
    public void removeParticipant(Participant p) {
    	if(! this.rtpSession.mcSession)
    		this.receivers.remove(p);
    	
    	this.ssrcTable.remove(p.ssrc, p);
    }

    /**
     * Find a participant based on the ssrc
     * 
     * @param ssrc of the participant to be found
     * @return the participant, null if unknonw
     */
    public Participant getParticipant(long ssrc) {
    	Participant p = null;
    	p = ssrcTable.get(ssrc);
    	return p; 
    }

    /**
     * Iterator for all the unicast receivers.
     * 
     * This one is used by both RTP for sending packets, as well as RTCP.
     * 
     * @return iterator for unicast participants
     */
    public Iterator<Participant> getUnicastReceivers() {
    	if(! this.rtpSession.mcSession) {
    		return this.receivers.iterator();
    	} else {
    		System.out.println("Request for ParticipantDatabase.getUnicastReceivers in multicast session");
    		return null;
    	}
    }

    /**
     * Enumeration of all the participants with known ssrcs.
     * 
     * This is primarily used for sending packets in multicast sessions.
     * 
     * @return enumerator with all the participants with known SSRCs
     */
    public Enumeration<Participant> getParticipants() {
    	return this.ssrcTable.elements();
    }

}
