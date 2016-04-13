package com.sengled.cloud.mediaserver.rtsp;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sengled.cloud.mediaserver.rtsp.codec.InterleavedFrame;
import com.sengled.cloud.mediaserver.rtsp.mq.RtspListener;

public class Sessions {
    private static final Logger logger = LoggerFactory.getLogger(Sessions.class);
    private static final Sessions instance = new Sessions();
    
    
    private final static String SDP_URL;
    static {
        String tmpDir = System.getProperty("java.io.tmpdir");
        String sdpUrl = tmpDir + "/sengled/media/sdps";
        
        File directory = new File(sdpUrl);
        try {
            FileUtils.deleteDirectory(directory);
        } catch (IOException e) {
            logger.warn("{} on delete dir {}", e.getMessage(), sdpUrl);
        }
        
        directory.mkdirs();
        
        if (directory.exists()) {
            logger.info("use {} to save sdp(s)", sdpUrl);
        } else {
            sdpUrl = ".";
        }
        
        SDP_URL = sdpUrl;
    }
    
    
    private Map<String, String> uriSdp = new ConcurrentHashMap<String, String>();
    private ConcurrentHashMap<String, List<RtspListener>> dispatchers = new ConcurrentHashMap<String, List<RtspListener>>();
    
    private ExecutorService threads = Executors.newFixedThreadPool(1);
    
    private Sessions(){}
    
    public static Sessions getInstance() {
        return instance;
    }
    
    
    public String getSdp(String uri) {
        return getInstance().uriSdp.get(uri);
    }
    
    public String removeSdp(final String uri, final String sdp) {
        String oldSdp = getInstance().uriSdp.get(uri);
        if (StringUtils.equals(sdp, oldSdp)) {
            oldSdp = getInstance().uriSdp.remove(uri);
        }
        
        // delete sdp
        final File file = getSdpFile(uri);
        threads.submit(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                logger.info("delete sdp of '{}', at {}", uri, file.getAbsolutePath());

                File newFile = new File(file.getParentFile(), file.getName() + ".deleted");
                newFile.delete();
                file.renameTo(newFile);
                return null;
            }
        });
        
        return oldSdp;
    }


    
    public String updateSdp(final String uri, final String sdp) {
        String oldSdp = getInstance().uriSdp.put(uri, sdp);
        
        
        // save sdp
        final File file = getSdpFile(uri);
        threads.submit(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                FileUtils.write(file, sdp);
                logger.info("update sdp of '{}', at {}", uri, file.getAbsolutePath());
                return null;
            }
        });
        
        
        return oldSdp;
    }

    public void register(String uri, RtspListener listener) {
        if (null != listener) {
            dispatchers.putIfAbsent(uri, new CopyOnWriteArrayList<RtspListener>());
            dispatchers.get(uri).add(listener);
        }
    }

    public void unregister(String uri, RtspListener listener) {
        if (null != listener) {
            dispatchers.putIfAbsent(uri, new CopyOnWriteArrayList<RtspListener>());
            dispatchers.get(uri).remove(listener);
        }
    }
    
    public void dispatch(String uri,
                         InterleavedFrame msg) {
        List<RtspListener> listen = dispatchers.get(uri);
        if (null != listen) {
            logger.trace("dispatch {} to {} listener(s) ", msg, listen.size());

            for (RtspListener rtspListener : listen) {
                rtspListener.onRTPFrame(msg.retain().duplicate());
            }

        } else {
            logger.trace("NO Listeners For {}", uri);
        }
        
    }
    
    
    
    private static File getSdpFile(String uri) {
        String tmpUrl = SDP_URL + (uri.startsWith("/") ? uri : ("/" + uri));
        if (tmpUrl.contains("?")) {
            tmpUrl = tmpUrl.substring(0, tmpUrl.indexOf("?"));
        }
        
        File tmpFile = new File(tmpUrl);
        File parent = tmpFile.getAbsoluteFile().getParentFile();
        if (!parent.exists() && !parent.mkdirs()) {
            logger.warn("fail create tmp dir '{}'", parent.getAbsolutePath());
        }

        return tmpFile;
    }
}
