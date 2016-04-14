package com.sengled.cloud.mediaserver.event;

import java.util.concurrent.CopyOnWriteArrayList;

public class Dispatcher {
    private CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<Listener>();
    
    public void addListener(Listener listener) {
        this.listeners.add(listener);
    }
    
    
    public void removeListener(Listener listener) {
        this.listeners.remove(listener);
    }

    public void dispatch(Event event) {
        for (Listener listener : listeners) {
            listener.on(event);
        }
    }
}
