package com.sengled.cloud.mediaserver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MediaServer {
    private static final Logger logger = LoggerFactory.getLogger(MediaServer.class);
    
    public static void main(String[] args) throws InterruptedException {
        int[] ports = new int[Math.max(1, args.length)];
        ports[0] = 5454;
        
        for (int i = 0; i < args.length; i++) {
            ports[i] = Integer.parseInt(args[i]);
        }

        RtspServer rtsp = new RtspServer().withHandlerClass(RtspServerInboundHandler.class);
        for (int i = 0; i < ports.length; i++) {
            try {
                rtsp.listen(ports[i], "0.0.0.0");
            } catch(Exception e) {
                logger.error("fail listen port[{}] for {}", ports[i], e);
            }
        }
    }
}
