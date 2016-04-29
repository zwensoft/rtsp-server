package com.sengled.cloud.mediaserver.rtsp.event;

import com.sengled.cloud.mediaserver.rtsp.NtpTime;

/**
 * Ntp 时间
 * 
 * <p>
 * 客户端推流的时候， 会向服务器发送  ntp 时间。
 * 由于服务端接收客户端 rtp 包有延时， 使用 {@link NtpTimeEvent} 
 * 可以同步发送端（灯）和接收端（播放器）的时间，避免使用服务器时间造成时间不同步.
 * @author 陈修恒
 * @date 2016年4月29日
 */
public class NtpTimeEvent extends AbstractRTPEvent<NtpTime> {

    public NtpTimeEvent(int streamIndex, NtpTime source) {
        super(streamIndex, source);
    }

    @Override
    protected void doDestroy() {
        
    }
}
