package com.sengled.cloud.mediaserver.rtsp.codec;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.util.ReferenceCountUtil;

import java.io.IOException;
import java.util.List;

import com.sengled.cloud.mediaserver.rtsp.interleaved.FullRtpPkt;
import com.sengled.cloud.mediaserver.rtsp.interleaved.RtpPkt;

/**
 * 将多个 rtp 按时间戳合并成一个包
 * 
 * @author 陈修恒
 * @date 2016年4月27日
 */
public class RtpObjectAggregator extends MessageToMessageDecoder<RtpPkt> {

    private int channel;
    private int maxFullRtpPktSize = 2 * 1024 * 1024;
    
    private FullRtpPkt group = null;
    
    

    public RtpObjectAggregator(int channel) {
        this.channel = channel;
    }

    public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
        ReferenceCountUtil.release(group);
        group = null;

        super.channelUnregistered(ctx);
        
    };
    

    @Override
    public boolean acceptInboundMessage(Object msg) throws Exception {
        if (msg instanceof RtpPkt) {
            RtpPkt rtp = (RtpPkt)msg;
            
            return rtp.channel() == channel;
        }
        
        return false;
    }
    
    @Override
    protected void decode(ChannelHandlerContext ctx,
                          RtpPkt msg,
                          List<Object> out) throws Exception {
        if (null == group) {
            group = new FullRtpPkt(msg.retain());
        } else if (group.getTimestamp() == msg.getTimestamp()){
            group.addRtp(msg.retain());
        } else {
            out.add(group);
            group = new FullRtpPkt(msg.retain());
        }
        
		if (group.dataLength() > maxFullRtpPktSize) {
			Exception ex = new IOException("AVPacket's size " + group.dataLength() + " > " + maxFullRtpPktSize);
        	group.release();
        	group = null;
        	
        	throw ex;
        }
    }

}
