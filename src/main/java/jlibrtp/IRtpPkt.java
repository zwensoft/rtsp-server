package jlibrtp;



public interface IRtpPkt {

    public abstract int dataLength();

    public abstract int getSeqNumber();

    public abstract long getTimestamp();

    public abstract long ssrc();

}
