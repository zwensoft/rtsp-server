package com.sengled.cloud.mediaserver;

import java.net.ConnectException;

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

        try {
            String uri = "rtsp://localhost:554/210360B871EECBD4D0AE1B9DCC24C568.sdp";
            RtspClient client = RtspClients.open(uri);
            System.out.println(client);
        } catch (ConnectException ex) {
            ex.printStackTrace();
        }
    }
}
