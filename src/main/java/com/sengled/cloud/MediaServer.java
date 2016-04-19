package com.sengled.cloud;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;

import org.apache.commons.io.IOUtils;
import org.dom4j.DocumentException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sengled.cloud.mediaserver.RtspClients;
import com.sengled.cloud.mediaserver.RtspServer;
import com.sengled.cloud.mediaserver.xml.MediaServerConfigs;
import com.sengled.cloud.mediaserver.xml.StreamSourceDef;

/**
 * rtsp media server
 * 
 * @author 陈修恒
 */
public class MediaServer {
    private static final Logger logger = LoggerFactory.getLogger(MediaServer.class);
    
    public static void main(String[] args) throws InterruptedException, IOException, DocumentException {
        MediaServerConfigs configs;
        InputStream in = null;
        try{
        	if (args.length > 1) {
        		in = new FileInputStream(args[0]);
        	} else {
        		in = MediaServer.class.getResourceAsStream("/config/server.xml");
        	}
        	configs = MediaServerConfigs.load(in);
        } finally {
        	IOUtils.closeQuietly(in);
        }
        
    	int[] ports = configs.getPorts();
        RtspServer rtsp = new RtspServer();
        for (int i = 0; i < ports.length; i++) {
            try {
                rtsp.listen(ports[i], "0.0.0.0");
            } catch(Exception e) {
                logger.error("fail listen port[{}] for {}", ports[i], e);
            }
        }

        
        for (StreamSourceDef def : configs.getStreamSources()) {
        	try {
                RtspClients.open(def.getUrl(), def.getName());
            } catch (ConnectException ex) {
                ex.printStackTrace();
            }
            	
		}
        
        /**
        */
        
        
        /**
        try {
            String uri = "rtsp://darwin-server:554/notExisted.sdp";
            RtspClient client = RtspClients.open(uri);
            System.out.println(client);
        } catch (ConnectException ex) {
            ex.printStackTrace();
        }
        
        */

        /**
        try {
            String uri = "rtsp://darwin-server:5544/urlError.sdp";
            RtspClient client = RtspClients.open(uri);
            System.out.println(client);
        } catch (ConnectException ex) {
            ex.printStackTrace();
        }
        */
    }
}
