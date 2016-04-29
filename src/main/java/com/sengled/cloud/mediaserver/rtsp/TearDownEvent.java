package com.sengled.cloud.mediaserver.rtsp;

/**
 * 流关闭了
 * 
 * @author 陈修恒
 * @date 2016年4月29日
 */
public class TearDownEvent extends AbstractRTPEvent<String> {

    /**
     * @param streamIndex
     * @param reason 关闭原因
     */
    public TearDownEvent(int streamIndex, String reason) {
        super(streamIndex, reason);
    }

    @Override
    protected void doDestroy() {
        
    }

}
