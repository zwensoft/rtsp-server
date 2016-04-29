package com.sengled.cloud.mediaserver.rtsp;

import com.sengled.cloud.mediaserver.event.Event;



public interface RtpEvent<T> extends Event  {
    public int getStreamIndex();

    public T getSource();

    public boolean isDestroyed();
    
    public void destroy();
}
