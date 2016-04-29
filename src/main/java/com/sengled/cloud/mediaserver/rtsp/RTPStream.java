package com.sengled.cloud.mediaserver.rtsp;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.sdp.Media;
import javax.sdp.MediaDescription;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RTPStream {
    public static final int SSRC_UNKNOWN = -1;

    private static final Logger logger = LoggerFactory.getLogger(RTPStream.class);
    
    private int rtpChannel;
    private int rtcpChannel;
    private MediaDescription md;
    private int streamIndex;
    private long ssrc = SSRC_UNKNOWN;
    
    private boolean isAudio;
    private boolean isVideo;
    private String codec;
    private Rational timeUnit;
    private int channels;

    
    public RTPStream(int streamIndex, MediaDescription md, int rtpChannel, int rtcpChannel) {
        this.md = md;
        this.streamIndex = streamIndex;
        this.rtcpChannel = rtcpChannel;
        this.rtpChannel = rtpChannel;
        
        this.timeUnit = Rational.$_1_000;
        this.channels = 1;
        try {
            Media media = md.getMedia();
            if (null != media) {
                isAudio = "audio".equals(media.getMediaType());
                isVideo = "video".equals(media.getMediaType());
            }
            if (isAudio) {
                channels = 1;
                timeUnit = Rational.$_8_000;
            } else if (isVideo) {
                channels = 0;
                timeUnit = Rational.$90_000;
            }
            
            
            String rtpmap = md.getAttribute("rtpmap");
            if (null != rtpmap) {
                Matcher matcher =Pattern.compile("([\\d]+) ([^/]+)/([\\d]+)(/([\\d]+))?").matcher(rtpmap);
                if (matcher.find()) {
                    // payloadType = Integer.parseInt(matcher.group(1));
                    codec = matcher.group(2);
                    timeUnit = Rational.valueOf(Integer.parseInt(matcher.group(3)));
                    
                    if (null != matcher.group(5)) {
                        this.channels = Integer.parseInt(matcher.group(5));
                    }
                }
            }

        } catch(Exception e) {
            logger.error("{}", e.getMessage(), e);
        }
    }

    public void setSsrc(long ssrc) {
        this.ssrc = ssrc;
    }

    public Rational getTimeUnit() {
        return timeUnit;
    }
    
    public MediaDescription getMediaDescription() {
        return md;
    }

    public int getRtcpChannel() {
        return rtcpChannel;
    }

    public int getRtpChannel() {
        return rtpChannel;
    }
    
    public boolean isAudio() {
        return isAudio;
    }

    public long getSsrc() {
        return ssrc;
    }

    public boolean isVideo() {
        return isVideo;
    }


    public String getCodec() {
        return codec;
    }


    public int getChannels() {
        return channels;
    }

    public int getStreamIndex() {
        return streamIndex;
    }
    
    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append("{RtpStream, sdp=\r\n")
           .append(md)
           .append("}");
        return buf.toString();
    }
}
