package com.sengled.cloud.mediaserver.rtsp;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

import javax.sdp.SessionDescription;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.eventbus.AsyncEventBus;
import com.sengled.cloud.mediaserver.rtsp.event.RtspSessionRemovedEvent;
import com.sengled.cloud.mediaserver.rtsp.event.RtspSessionUpdatedEvent;
import com.sengled.cloud.mediaserver.rtsp.event.TearDownEvent;

/**
 * 一个 server 实例
 * 
 * @author 陈修恒
 * @date 2016年5月6日
 */
public class ServerEngine {
    
    private static final Logger logger = LoggerFactory.getLogger(ServerEngine.class);
    
    private final AsyncEventBus eventBus = new AsyncEventBus(Executors.newSingleThreadExecutor());
    private ConcurrentHashMap<String, RtspSessionAndListeners> sessionAndListeners = new ConcurrentHashMap<String, RtspSessionAndListeners>();
   
    
    public ServerEngine(){}
    
    public AsyncEventBus eventBus() {
        return eventBus;
    }
    
    public RtspSession removeSession(final String name, final RtspSession session) {
        RtspSessionAndListeners element = sessionAndListeners.get(name);
        if (null == element || element.session != session) {
            return null;
        }
        
        eventBus.post(new RtspSessionRemovedEvent(session));
        
        element.updateSession(null);
        sessionAndListeners.remove(name);
        
        return element.session;
    }
    
    public void updateSession(final String name, final RtspSession session) {
        sessionAndListeners.putIfAbsent(name, new RtspSessionAndListeners(session));
        
        RtspSessionAndListeners rtspSessionAndListeners = sessionAndListeners.get(name);
        rtspSessionAndListeners.updateSession(session);
        
        eventBus.post(new RtspSessionUpdatedEvent(session));
        logger.info("{} device session(s) online", numSessions());
    }

    public int register(String name, RtspSessionListener newItem) {
        if (null != newItem) {
            RtspSessionAndListeners  element = sessionAndListeners.get(name);
            
            if (null != element) {
                element.addRtspSessionListener(newItem);
                return element.numListeners();
            }
            
        }
        return 0;
    }

    public void unregister(String name, RtspSessionListener listener) {
        if (null != listener) {
            RtspSessionAndListeners  element = sessionAndListeners.get(name);
            
            if (null != element) {
                element.removeRtspSessionListener(listener);
            }
        }
    }
    
    public <T> void dispatch(String name, RtpEvent<T> event) {
        RtspSessionAndListeners  element = sessionAndListeners.get(name);
        
        if (null != element) {
            element.dispatch(event);
        } else {
            logger.trace("NO Listeners For {}", name);
        }
    }

    public Collection<String> sessionNames() {
        return sessionAndListeners.keySet();
    }

    public int numSessions() {
        return sessionAndListeners.size();
    }

    public SessionDescription getSessionDescription(String uri) {
        RtspSessionAndListeners session = sessionAndListeners.get(uri);
        
        return null != session ? session.getSessionDescription() : null;
    }

    
    public static class RtspSessionAndListeners {
        private RtspSession session;
        private List<RtspSessionListener> listeners = new CopyOnWriteArrayList<RtspSessionListener>();
        
        
        public RtspSessionAndListeners(RtspSession session) {
            super();
            this.session = session;
        }
       

        public int numListeners() {
            return listeners.size();
        }

        public void addRtspSessionListener(RtspSessionListener listener) {
            listeners.remove(listener);
            listeners.add(listener);
        }
        
        public void removeRtspSessionListener(RtspSessionListener listener) {
            listeners.remove(listener);
        }
        
        public void updateSession(RtspSession session) {
            this.session = session;
            
            dispatch(new TearDownEvent("session-clean"));
            listeners.clear();
        }
    
        public <T> void dispatch(RtpEvent<T> event) {

            for (RtspSessionListener rtspListener : listeners) {
                try{
                    rtspListener.on(event);
                } catch(Exception ex){
                    // 独立 Listener 的异常不能传播到其他 listener
                    logger.warn("{}#on({}) Failed.", rtspListener, event, ex);
                    rtspListener.fireExceptionCaught(ex);
                }
            }
        }

        public SessionDescription getSessionDescription() {
            return null != session ? session.getSessionDescription() : null;
        }
    }
}
