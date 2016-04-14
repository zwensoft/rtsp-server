package com.sengled.cloud.mediaserver.rtsp;

import io.netty.util.ReferenceCountUtil;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.sdp.SessionDescription;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sengled.cloud.mediaserver.event.Listener;
import com.sengled.cloud.mediaserver.rtsp.rtp.RtpEvent;

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
    
    
    private Map<String, RtspSession> sessions = new ConcurrentHashMap<String, RtspSession>();
    private ConcurrentHashMap<String, List<Listener>> dispatchers = new ConcurrentHashMap<String, List<Listener>>();
    
    private ExecutorService threads = Executors.newFixedThreadPool(1);
    
    private Sessions(){}
    
    public static Sessions getInstance() {
        return instance;
    }
    
    
    public SessionDescription getSdp(String uri) {
        RtspSession session = getInstance().sessions.get(uri);
        
        return null != session ? session.getSessionDescription() : null;
    }
    
    public RtspSession removeSession(final String uri, final RtspSession session) {
        RtspSession oldSession = getInstance().sessions.get(uri);
        if (oldSession == session) {
            oldSession = getInstance().sessions.remove(uri);
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
        
        return oldSession;
    }


    
    public RtspSession updateSession(final String uri, final RtspSession session) {
        RtspSession oldSession = getInstance().sessions.put(uri, session);
        
        // save sdp
        final File file = getSdpFile(uri);
        threads.submit(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                FileUtils.write(file, session.getSDP());
                logger.info("update sdp of '{}', at {}", uri, file.getAbsolutePath());
                return null;
            }
        });
        
        
        return oldSession;
    }

    public void register(String uri, Listener listener) {
        if (null != listener) {
            dispatchers.putIfAbsent(uri, new CopyOnWriteArrayList<Listener>());
            dispatchers.get(uri).add(listener);
        }
    }

    public void unregister(String uri, Listener listener) {
        if (null != listener) {
            dispatchers.putIfAbsent(uri, new CopyOnWriteArrayList<Listener>());
            dispatchers.get(uri).remove(listener);
        }
    }
    
    public void dispatch(String uri, RtpEvent msg) {
        try {
            List<Listener> listen = dispatchers.get(uri);
            if (null != listen) {
                logger.trace("dispatch {} to {} listener(s) ", msg, listen.size());

                for (Listener rtspListener : listen) {
                    rtspListener.on(msg.duplicate());
                }

            } else {
                logger.trace("NO Listeners For {}", uri);
            }
        } finally {
            ReferenceCountUtil.release(msg);
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
