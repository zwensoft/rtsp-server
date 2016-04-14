package com.sengled.cloud.mediaserver.rtsp;

public class Clock {
    private boolean isStarted;
    private long startAt;
    
    public void start() {
        this.isStarted = true;
        this.startAt = System.currentTimeMillis();
    }
    
    public boolean isStarted() {
        return isStarted;
    }
    
    public long getTimeMillis() {
        return System.currentTimeMillis() - this.startAt;
    }
}
