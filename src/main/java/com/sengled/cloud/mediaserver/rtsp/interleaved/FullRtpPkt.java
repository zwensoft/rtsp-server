package com.sengled.cloud.mediaserver.rtsp.interleaved;

import io.netty.util.ReferenceCounted;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.sengled.cloud.mediaserver.rtsp.InterLeaved;

/**
 * 一个完整的音,视频帧
 * 
 * @author 陈修恒
 * @date 2016年4月27日
 */
public class FullRtpPkt implements ReferenceCounted, InterLeaved {
    private AtomicInteger refCnt = new AtomicInteger(1);
    final private List<RtpPkt> contents;

    public FullRtpPkt(RtpPkt rtp)  {
        super();
        contents = new ArrayList<RtpPkt>();
        contents.add(rtp);
    }
    

    private FullRtpPkt(List<RtpPkt> newPkts) {
        this.contents = newPkts;
    }


    public RtpPkt first() {
        return contents.get(0);
    }
    

    public void setTimestamp(long timestamp) {
        for (RtpPkt rtpObject : contents) {
            rtpObject.setTimestamp(timestamp);
        }
    }
    
    public long ssrc() {
        return first().ssrc();
    }
    
    public long getTimestamp() {
        return first().getTimestamp();
    }
    
    public int channel() {
        return first().channel();
    }
    
    public int getPayloadType() {
        return first().getPayloadType();
    }
    
    public int getSeqNo() {
        return first().getSeqNumber();
    }
    
    public int numRtp() {
        return contents.size();
    }

    public boolean isSamePacket(RtpPkt content) {
        return content.ssrc() == ssrc() && content.getTimestamp() == getTimestamp();
    }
    /**
     * @param content
     * @return true 表示 新加入的 rtp 包与现有的包属于同一组
     */
    public void addRtp(RtpPkt content) {
        contents.add(content);
    }

    public List<RtpPkt> contents() {
        return contents;
    }
    
    public int numContents() {
        return contents.size();
    }
    

    public int dataLength() {
        int readableBytes = 0;
        for (RtpPkt rtpObject : contents) {
            readableBytes += rtpObject.dataLength();
        }
        return readableBytes;
    }

    public int length() {
        int length = 0;
        
        for (RtpPkt rtpContent : contents) {
            length += rtpContent.content().readableBytes();
        }
        
        return length;
    }
    
    
    
    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append("{FullRtpPkt[").append(numContents()).append("]");
        buf.append(", refCnt = ").append(refCnt());
        buf.append(", channel = ").append(channel());
        buf.append(", timestamp = ").append(getTimestamp());
        buf.append(", pt = ").append(getPayloadType());
        buf.append(", length = ").append(length());
        buf.append("}");
        
        return buf.toString();
    }


    @Override
    public int refCnt() {
        return refCnt.get();
    }


    public FullRtpPkt duplicate() {
        List<RtpPkt> newPkts = new ArrayList<RtpPkt>(numContents());
        for (RtpPkt rtpPkt : contents) {
            newPkts.add(rtpPkt.duplicate());
        }
        
        return new FullRtpPkt(newPkts);
    }
    
    @Override
    public FullRtpPkt retain() {
        for (RtpPkt rtpPkt : contents) {
            rtpPkt.retain();
        }
        
        refCnt.incrementAndGet();
        return this;
        
    }


    @Override
    public FullRtpPkt retain(int increment) {
        for (RtpPkt rtpPkt : contents) {
            rtpPkt.retain(increment);
        }

        refCnt.addAndGet(increment);
        return this;
    }


    @Override
    public boolean release() {
        boolean released = true;
        for (RtpPkt rtpPkt : contents) {
            if (null != rtpPkt) {
                released = rtpPkt.release() && released;
            }
        }
        
        refCnt.decrementAndGet();
        return released;
    }


    @Override
    public boolean release(int decrement) {
        boolean released = true;
        for (RtpPkt rtpPkt : contents) {
            if (null != rtpPkt) {
                released = rtpPkt.release(decrement) && released;
            }
        }
        
        refCnt.getAndAdd(- decrement);
        return released;
    }

}
