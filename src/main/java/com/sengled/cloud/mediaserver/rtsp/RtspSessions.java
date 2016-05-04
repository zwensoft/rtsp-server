package com.sengled.cloud.mediaserver.rtsp;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

import javax.sdp.SessionDescription;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.eventbus.AsyncEventBus;
import com.sengled.cloud.mediaserver.rtsp.event.RtspSessionRemovedEvent;
import com.sengled.cloud.mediaserver.rtsp.event.RtspSessionUpdatedEvent;

public class RtspSessions {
    
    private static final Logger logger = LoggerFactory.getLogger(RtspSessions.class);
    private static final RtspSessions instance = new RtspSessions();
    
    private final AsyncEventBus sessionEventBus = new AsyncEventBus(Executors.newSingleThreadExecutor());
    
    
    private Map<String, RtspSession> sessions = new ConcurrentHashMap<String, RtspSession>();
    private ConcurrentHashMap<String, List<RtspSessionListener>> dispatchers = new ConcurrentHashMap<String, List<RtspSessionListener>>();
   
    
    private RtspSessions(){}
    
    public static RtspSessions getInstance() {
        return instance;
    }
    
    public AsyncEventBus sessionEventBus() {
        return sessionEventBus;
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
        
        if (null != oldSession) {
            sessionEventBus.post(new RtspSessionRemovedEvent(oldSession));
        }
        
        logger.info("{} device session(s) online", numSessions());
        return oldSession;
    }


    
    public RtspSession updateSession(final String name, final RtspSession session) {
        RtspSession oldSession = getInstance().sessions.put(name, session);
        
        sessionEventBus.post(new RtspSessionUpdatedEvent(session));
        
       
        logger.info("{} device session(s) online", numSessions());
        return oldSession;
    }

    public void register(String name, RtspSessionListener listener) {
        if (null != listener) {
            dispatchers.putIfAbsent(name, new CopyOnWriteArrayList<RtspSessionListener>());
            dispatchers.get(name).add(listener);
            
            RtspSession producer = sessions.get(name);
            if (null != producer) {
                listener.init(producer);
            }
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

    public Collection<String> sessionNames() {
        return sessions.keySet();
    }

    public int numSessions() {
        return sessions.size();
    }
    
    
   
}
