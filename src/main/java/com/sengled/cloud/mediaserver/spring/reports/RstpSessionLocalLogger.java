package com.sengled.cloud.mediaserver.spring.reports;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

import com.google.common.eventbus.Subscribe;
import com.sengled.cloud.mediaserver.rtsp.RtspSession;
import com.sengled.cloud.mediaserver.rtsp.RtspSessions;
import com.sengled.cloud.mediaserver.rtsp.event.RtspSessionUpdatedEvent;
import com.sengled.cloud.mediaserver.rtsp.event.RtspSessionRemovedEvent;

/**
 * 把 {@link RtspSession}的 sdp 保存到本地， 以便查日志
 *  
 * @author 陈修恒
 * @date 2016年5月3日
 */
public class RstpSessionLocalLogger implements InitializingBean {
    private static Logger logger = LoggerFactory.getLogger(RstpSessionLocalLogger.class);
    private ExecutorService threads = Executors.newFixedThreadPool(1);
    
    private final static String SDP_URL;
    static {
        SDP_URL = getSdpUrl();
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        RtspSessions.getInstance().sessionEventBus().register(this);
    }

    @Subscribe
    public void onSessionCreated(RtspSessionUpdatedEvent event) {
        final RtspSession session = event.getSession();
        
        // save sdp
        final File file = getSdpFile(session);
        threads.submit(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                FileUtils.write(file, session.getSDP());
                logger.info("update sdp '{}'", file.getAbsolutePath());
                return null;
            }
        });
    }
    
    
    @Subscribe
    public void onSessionRemoved(RtspSessionRemovedEvent event) {
        final RtspSession session = event.getSession();
        
        // delete sdp
        final File file = getSdpFile(session);
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
