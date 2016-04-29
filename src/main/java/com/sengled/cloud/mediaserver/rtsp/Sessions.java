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

import javax.sdp.SessionDescription;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Sessions {
    private static final Logger logger = LoggerFactory.getLogger(Sessions.class);
    private static final Sessions instance = new Sessions();
    
    
    private final static String SDP_URL;
    static {
        String tmpDir = System.getProperty("java.io.tmpdir");
        String sdpUrl = FilenameUtils.normalize(tmpDir + "/sengled/media/sdps");
        
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
    private ConcurrentHashMap<String, List<RtspSessionListener>> dispatchers = new ConcurrentHashMap<String, List<RtspSessionListener>>();
    
    private ExecutorService threads = Executors.newFixedThreadPool(1);
    
    private Sessions(){}
    
    public static Sessions getInstance() {
        return instance;
    }
    
    
    public SessionDescription getSessionDescription(String uri) {
        RtspSession session = getInstance().sessions.get(uri);
        
        return null != session ? session.getSessionDescription() : null;
    }
    
    public RtspSession removeSession(final String name, final RtspSession session) {
        RtspSession oldSession = getInstance().sessions.get(name);
        if (oldSession == session) {
            oldSession = getInstance().sessions.remove(name);
        }
        
        // delete sdp
        final File file = getSdpFile(name);
        threads.submit(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                logger.info("delete sdp '{}'", file.getAbsolutePath());

                File newFile = new File(file.getParentFile(), file.getName() + ".deleted");
                newFile.delete();
                file.renameTo(newFile);
                return null;
            }
        });
        
        logger.info("{} rtsp session(s) online", sessions.size());
        return oldSession;
    }


    
    public RtspSession updateSession(final String name, final RtspSession session) {
        RtspSession oldSession = getInstance().sessions.put(name, session);
        
        // save sdp
        final File file = getSdpFile(name);
        threads.submit(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                FileUtils.write(file, session.getSDP());
                logger.info("update sdp '{}'", file.getAbsolutePath());
                return null;
            }
        });
        
        logger.info("{} rtsp session(s) online", sessions.size());
        return oldSession;
    }

    public void register(String name, RtspSessionListener listener) {
        if (null != listener) {
            dispatchers.putIfAbsent(name, new CopyOnWriteArrayList<RtspSessionListener>());
            dispatchers.get(name).add(listener);
        }
    }

    public void unregister(String uri, RtspSessionListener listener) {
        if (null != listener) {
            dispatchers.putIfAbsent(uri, new CopyOnWriteArrayList<RtspSessionListener>());
            dispatchers.get(uri).remove(listener);
        }
    }
    
    public <T> void dispatch(String name, RtpEvent<T> event) {
        List<RtspSessionListener> listens = dispatchers.get(name);
        if (null != listens) {
            logger.trace("dispatch {} to {} listener(s) ", event, listens.size());

            for (RtspSessionListener rtspListener : listens) {
                try{
                    rtspListener.on(event);
                } catch(Exception ex){
                    // 独立 Listener 的异常不能传播到其他 listener
                    logger.warn("{}#on({}) Failed.", rtspListener, event, ex);
                    rtspListener.fireExceptionCaught(ex);
                }
            }

        } else {
            logger.trace("NO Listeners For {}", name);
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
