package com.sengled.cloud.mediaserver;


public class RtspRedirect {
    public static void main(String[] args) throws InterruptedException {
        String newServer = args.length > 0 ? args[0] : "rtsp://localhost:554/" ;
        int port = Integer.valueOf(args.length > 1 ? args[1] : "15454");

        RtspRedirectInboundHandler.setNewLocation(newServer);
        RtspBootstrap rtsp = new RtspBootstrap().withHandlerClass(RtspRedirectInboundHandler.class);
        rtsp.listen(port, "0.0.0.0");
    }
}
