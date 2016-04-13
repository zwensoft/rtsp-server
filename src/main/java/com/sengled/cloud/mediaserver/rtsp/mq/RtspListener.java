package com.sengled.cloud.mediaserver.rtsp.mq;

import com.sengled.cloud.mediaserver.rtsp.codec.InterleavedFrame;

public interface RtspListener {
    public void onRTPFrame(InterleavedFrame frame);
}
