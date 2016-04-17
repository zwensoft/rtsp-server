package com.sengled.cloud.mediaserver.rtsp.rtp;

import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.sdp.Media;
import javax.sdp.MediaDescription;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sengled.cloud.mediaserver.rtsp.Sessions;

public class RTPStream {
    private static final Logger logger = LoggerFactory.getLogger(RTPStream.class);
    
    private Sessions sessions = Sessions.getInstance();
    
    private int rtpChannel;
    private int rtcpChannel;
    private MediaDescription md;
    private int streamIndex;
    
    private boolean isAudio;
    private boolean isVideo;
    private String codec;
    private Rational unit;
    private int channels;

    final private long defaultDuration;
    final private long maxDuration;
    
    
    boolean isStarted = false;
    private long duration;
    private long timestamp = -1;
    private long rtpTimestamp;
    
    public RTPStream(int streamIndex, MediaDescription md, int rtpChannel, int rtcpChannel) {
        this.md = md;
        this.streamIndex = streamIndex;
        this.rtcpChannel = rtcpChannel;
        this.rtpChannel = rtpChannel;
        
        this.unit = Rational.$_1_000;
        this.channels = 1;
        try {
            Media media = md.getMedia();
            if (null != media) {
                isAudio = "audio".equals(media.getMediaType());
                isVideo = "video".equals(media.getMediaType());
            }
            
            @SuppressWarnings("unchecked")
            Vector<String> formats = media.getMediaFormats(false);
            if (null != formats) {
                for (String format : formats) {
                    if ("8".equals(format)) { // pcm_alaw
                        channels = 1;
                        unit = Rational.$_8_000;
                    }
                }
            }
            
            
            String rtpmap = md.getAttribute("rtpmap");
            if (null != rtpmap) {
                Matcher matcher =Pattern.compile("([\\d]+) ([^/]+)/([\\d]+)(/([\\d]+))?").matcher(rtpmap);
                if (matcher.find()) {
                    // payloadType = Integer.parseInt(matcher.group(1));
                    codec = matcher.group(2);
                    unit = Rational.valueOf(Integer.parseInt(matcher.group(3)));
                    
                    if (null != matcher.group(5)) {
                        this.channels = Integer.parseInt(matcher.group(5));
                    }
                }
            }

        } catch(Exception e) {
            logger.error("{}", e.getMessage(), e);
        }
        
        maxDuration = unit.convert(1000, Rational.$_1_000);
        if (isVideo) {
            defaultDuration = unit.convert(40, Rational.$_1_000);
        } else if (isAudio) {
            defaultDuration = unit.convert(23, Rational.$_1_000);
        } else {
            defaultDuration = unit.convert(33, Rational.$_1_000);
        }
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


    public boolean isVideo() {
        return isVideo;
    }


    public String getCodec() {
        return codec;
    }


    public int getChannels() {
        return channels;
    }


    public void dispatch(String uri, int streamIndex, RTPContent rtp) {
        fixTimestamp(rtp);
        
        logger.info("dispath {}, {}", getTimestampMillis() * 1.0/1000,  rtp);
        sessions.dispatch(uri, new RtpEvent(streamIndex, md, rtp));
    }
    
    private void fixTimestamp(RTPContent rtp) {
        if (isStarted()) {
            final long newRtpTimestamp = rtp.getTimestamp();
            final long newDuration = newRtpTimestamp - rtpTimestamp;
            if (newDuration != 0L) { // 时间戳变了
                if (Math.abs(newDuration) < maxDuration)  { // 新的时间戳间隔，在期望之内，使用新的间隔
                    duration = newDuration;
                }

                this.timestamp += duration;
                this.rtpTimestamp = newRtpTimestamp;
                
                if (logger.isTraceEnabled()) {
                    logger.trace("stream#{}, duration = {}ms",  streamIndex, Rational.$_1_000.convert(duration, unit));
                }
            }

            rtp.setTimestamp(timestamp);
        } else {
            this.isStarted = true;
            this.duration = defaultDuration; // 40ms一帧
            this.timestamp = 0;
            this.rtpTimestamp = rtp.getTimestamp();
        }
    }


    public final boolean isStarted() {
        return isStarted;
    }
    
    public final long getTimestampMillis() {
        return isStarted() ? Rational.$_1_000.convert(timestamp, unit) : -1;
    }
    
    public final void setTimestampMillis(long newTiemstampMillis) {
        timestamp = unit.convert(newTiemstampMillis, Rational.$_1_000);
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
