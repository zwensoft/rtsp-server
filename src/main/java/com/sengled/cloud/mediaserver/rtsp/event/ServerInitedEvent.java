package com.sengled.cloud.mediaserver.rtsp.event;

import com.sengled.cloud.mediaserver.rtsp.ServerContext;

/**
 * 服务器启动成功
 * 
 * @author 陈修恒
 * @date 2016年5月5日
 */
public class ServerInitedEvent {
    private int port;
    private ServerContext rtspServerContext;


    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public ServerContext getRtspServerContext() {
        return rtspServerContext;
    }

    public void setRtspServerContext(ServerContext sessions) {
        this.rtspServerContext = sessions;
    }


}
