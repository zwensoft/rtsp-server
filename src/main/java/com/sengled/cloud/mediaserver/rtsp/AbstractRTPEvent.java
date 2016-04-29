package com.sengled.cloud.mediaserver.rtsp;


public abstract class AbstractRTPEvent<T> implements RtpEvent<T>{
    protected int streamIndex;
    protected T source;
    private boolean destroy;
    
    public AbstractRTPEvent(int streamIndex, T source) {
        super();
        this.streamIndex = streamIndex;
        this.source = source;
    }

    @Override
    final public int getStreamIndex() {
        ensureNotDestroyed();
        
        return streamIndex;
    }

    @Override
    final public T getSource() {
        ensureNotDestroyed();
        
        return source;
    }

    @Override
    final public boolean isDestroyed() {
        return destroy;
    }

    @Override
    final public void destroy() {
        if (!destroy) {
            destroy = true;
            doDestroy();
        }
    }
    
    @Override
    protected void finalize() throws Throwable {
        destroy();
    }

    protected abstract void doDestroy();
    
    private void ensureNotDestroyed() {
        if (destroy) {
            throw new IllegalAccessError(this + " Has been destroyed");
        }
    }
    
    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append("{").append(getClass().getSimpleName());
        buf.append(", stream#").append(streamIndex);
        buf.append(", ").append(source);
        buf.append("}");
        return buf.toString();
    }
}
