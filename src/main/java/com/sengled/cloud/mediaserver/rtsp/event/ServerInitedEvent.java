package com.sengled.cloud.mediaserver.rtsp.event;

import com.sengled.cloud.mediaserver.rtsp.ServerEngine;

/**
 * 服务器启动成功
 * 
 * @author 陈修恒
 * @date 2016年5月5日
 */
public class ServerInitedEvent {
    private int port;
    private ServerEngine rtspServerContext;


    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public ServerEngine getRtspServerContext() {
        return rtspServerContext;
    }

    public void setRtspServerContext(ServerEngine sessions) {
        this.rtspServerContext = sessions;
    }


}
