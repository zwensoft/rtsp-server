package com.sengled.cloud.mediaserver.spring.reports;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Callable;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.eventbus.Subscribe;
import com.sengled.cloud.async.TimerExecutor;
import com.sengled.cloud.mediaserver.rtsp.RtspSession;
import com.sengled.cloud.mediaserver.rtsp.ServerEngine;
import com.sengled.cloud.mediaserver.rtsp.event.RtspSessionRemovedEvent;
import com.sengled.cloud.mediaserver.rtsp.event.RtspSessionUpdatedEvent;

/**
 * 把 {@link RtspSession}的 sdp 保存到本地， 以便查日志
 *  
 * @author 陈修恒
 * @date 2016年5月3日
 */
public class RtspSessionLogger {


    private static Logger logger = LoggerFactory.getLogger(RtspSessionLogger.class);
    private TimerExecutor executor = new TimerExecutor("write-rtsp-session-at-local");
    
    private final static String SDP_URL;
    static {
        SDP_URL = getSdpUrl();
    }


    public void register(int port, ServerEngine engine) {
        engine.eventBus().register(this);
    }
    
    
    @Subscribe
    public void onSessionCreated(RtspSessionUpdatedEvent event) {
        final RtspSession session = event.getSession();
        final int numSessions = event.getNumSessions();
        
        // save sdp
        final File file = getSdpFile(session);
        executor.setTimeout(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                FileUtils.write(file, session.getSDP());
                logger.info("save {}th sdp '{}'", numSessions, file.getAbsolutePath());
                return null;
            }
        }, 0);
    }
    
    
    @Subscribe
    public void onSessionRemoved(RtspSessionRemovedEvent event) {
        final RtspSession session = event.getSession();
        final int numSessions = event.getNumSessions();
        
        // delete sdp
        final File file = getSdpFile(session);
        executor.setTimeout(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                logger.info("delete {}th sdp '{}'", numSessions, file.getAbsolutePath());

                File newFile = new File(file.getParentFile(), file.getName() + ".deleted");
                newFile.delete();
                file.renameTo(newFile);
                return null;
            }
        }, 0);
    }
    
    private File getSdpFile(RtspSession session) {
        String name = session.getName();
        String tmpUrl = SDP_URL + (name.startsWith("/") ? name : ("/" + name));
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
 
    
    private static String getSdpUrl() {
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
        return sdpUrl;
    }

    
}
