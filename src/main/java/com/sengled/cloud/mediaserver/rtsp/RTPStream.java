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
    
    private MediaDescription md;
    private int streamIndex;
    
    private MediaType mediaType = MediaType.UNKOWN;
    private String codec;
    private Rational timeUnit;
    private int channels;

    
    public RTPStream(int streamIndex, MediaDescription md) {
        this.md = md;
        this.streamIndex = streamIndex;
        
        this.timeUnit = Rational.$_1_000;
        this.channels = 1;
        try {
            Media media = md.getMedia();
            if (null != media) {
                mediaType = MediaType.typeOf(media.getMediaType());
            }
            if (mediaType.isAudio()) {
                channels = 1;
                timeUnit = Rational.$_8_000;
            } else if (mediaType.isVideo()) {
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


    public Rational getTimeUnit() {
        return timeUnit;
    }
    
    public MediaDescription getMediaDescription() {
        return md;
    }
    
    public boolean isAudio() {
        return mediaType.isAudio();
    }

    public boolean isVideo() {
        return mediaType.isVideo();
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

    public long getTimestampMills(long timestamp) {
        return Rational.$_1_000.convert(timestamp, timeUnit);
    }

    public MediaType getMediaType() {
        return mediaType;
    }
    
    
}
