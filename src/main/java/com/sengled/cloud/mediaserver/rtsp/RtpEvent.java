package com.sengled.cloud.mediaserver.rtsp;




/**
 * RTP 事件
 * 
 * @author 陈修恒
 * @date 2016年5月3日
 * @param <T>
 */
public interface RtpEvent<T> {
    public int getStreamIndex();

    public T getSource();

    public boolean isDestroyed();
    
    public void destroy();
}
