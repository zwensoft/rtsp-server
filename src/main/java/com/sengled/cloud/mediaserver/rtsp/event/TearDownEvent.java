package com.sengled.cloud.mediaserver.rtsp.event;


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
    public TearDownEvent(String reason) {
        super(-1, reason);
    }

    public TearDownEvent(byte[] reason) {
        super(-1, null != reason ? new String(reason) : "");
	}

	@Override
    protected void doDestroy() {
        
    }

}
