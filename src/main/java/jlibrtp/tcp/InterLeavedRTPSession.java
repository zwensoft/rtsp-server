package jlibrtp.tcp;

import io.netty.channel.Channel;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

import jlibrtp.AbstractRTPSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RTP over tcp
 * @author 陈修恒
 * @date 2016年4月28日
 */
public class InterLeavedRTPSession extends AbstractRTPSession {
    private static final Logger logger = LoggerFactory.getLogger(InterLeavedRTPSession.class);
    
    private Channel channel;
    private int rtpChannel;
    
    public InterLeavedRTPSession(Channel channel, int rtpChannel, int rtcpChannel) {
        super(InterLeavedParticipantDatabase.FACTORY);
        
        this.channel = channel;
        this.generateCNAME();
        this.generateSsrc();
        
        this.rtcpSession = new InterLeavedRTCPSession(rtcpChannel);
    }


    public Channel channel() {
        return channel;
    }
    
    public int rtcpChannel() {
        return rtcpSession().rtcpChannel();
    }
    
    public int rtpChannel() {
        return rtpChannel;
    }
    
    public void rtpChannel(int rtpChannel) {
        this.rtpChannel = rtpChannel;
    }
   
    @Override
    public void endSession() {
        // TODO Auto-generated method stub
        logger.warn("NOT Implemented!");
    }

    @Override
    protected void generateCNAME() {
        SocketAddress addr = channel.localAddress();
        
        String hostname = null;
        
        if (addr instanceof InetSocketAddress) {
            InetSocketAddress inet = (InetSocketAddress)addr;
            hostname = inet.getHostName();
        }
        
        if((null == hostname || "0.0.0.0".equals(hostname)) && System.getenv("HOSTNAME") != null) {
          hostname = System.getenv("HOSTNAME");
        }
        
        cname = System.getProperty("user.name") + "@" + hostname;
    }

   
    
    public InterLeavedRTCPSession rtcpSession() {
        return (InterLeavedRTCPSession)rtcpSession;
    }
    

}
