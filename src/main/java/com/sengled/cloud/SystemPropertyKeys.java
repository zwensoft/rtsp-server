package com.sengled.cloud;

/**
 * 系统参数
 * 
 * {@link System#getProperty(String)}
 * @author 陈修恒
 * @date 2016年5月11日
 */
public interface SystemPropertyKeys {
    /** netty 的工作线程数 **/
    public static final String WORKER_THREADS = "workerThreads";
    
    /** 需要等待视频开始播放了， 才开始播放音频 **/
    public static final String PLAY_AUDIO_UNTIL_VIDEO_START = "playAudioUntilVideoStart";
}
