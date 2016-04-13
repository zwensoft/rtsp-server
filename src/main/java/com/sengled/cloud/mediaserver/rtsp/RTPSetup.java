package com.sengled.cloud.mediaserver.rtsp;

public class RTPSetup {
    private String uri;
    private int seq;
    private long rtpTime;
    
    RTPSetup(String url) {
        this.uri = url;
        this.seq = (int)(10 * 1000 * Math.random());
        this.rtpTime =  (int)(1000 * 1000 * Math.random());
    }
    
    
    public long getRtpTime() {
        return rtpTime;
    }
    
    public int getSeq() {
        return seq;
    }
    
    public String getUrl(String baseUrl) {
        if (null == baseUrl) {
            return uri;
        } else if(baseUrl.endsWith("/") && uri.startsWith("/")) {
            return baseUrl + uri.substring(1);
        } else if (!baseUrl.endsWith("/") && !uri.startsWith("/")) {
            return baseUrl + "/" + uri;
        } else {
            return baseUrl + uri;
        }
    }
    
    public String getUri() {
        return uri;
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append("url=").append(uri);
        //buf.append(";seq=").append(seq);
        //buf.append(";rtptime=").append(rtpTime);
        
        return buf.toString();
    }
}