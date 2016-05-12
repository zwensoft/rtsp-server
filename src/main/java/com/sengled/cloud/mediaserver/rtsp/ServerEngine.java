package com.sengled.cloud.mediaserver.rtsp;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

import javax.sdp.SessionDescription;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.google.common.eventbus.AsyncEventBus;
import com.google.common.eventbus.EventBus;
import com.sengled.cloud.mediaserver.rtsp.event.RtspSessionRemovedEvent;
import com.sengled.cloud.mediaserver.rtsp.event.RtspSessionUpdatedEvent;

/**
 * 一个 server 实例
 * 
 * @author 陈修恒
 * @date 2016年5月6日
 */
final public class ServerEngine {

    private static final Logger logger = LoggerFactory.getLogger(ServerEngine.class);

    private final EventBus eventBus = new AsyncEventBus(Executors.newSingleThreadExecutor());
    private ConcurrentHashMap<String, Dispatcher> dispatchers =
            new ConcurrentHashMap<String, Dispatcher>();

    private boolean usedMetricRegistry = false;
    // 连接数统计
    private Counter channelsCounter;
    // input 方向的 IO 流量
    private Meter inboundIoMeter;
    // output 方向的 IO 流量
    private Meter outboundIoMeter;
    // dispatcher 连接数统计
    private Counter inboundSessionCounter;
    
    public ServerEngine() {

    }

    public ServerEngine withMetricRegistry(String name, MetricRegistry registry) {
        usedMetricRegistry = true;
        channelsCounter =
                registry.counter(MetricRegistry.name(ServerEngine.class, name, "channels"));
        inboundSessionCounter =
                registry.counter(MetricRegistry.name(ServerEngine.class, name, "inboundSession"));
        inboundIoMeter = registry.meter(MetricRegistry.name(ServerEngine.class, name, "inbound"));
        outboundIoMeter = registry.meter(MetricRegistry.name(ServerEngine.class, name, "outbound"));
        
        return this;
    }
    
    public EventBus eventBus() {
        return eventBus;
    }

    public RtspSession removeSession(final String name,
                                     final RtspSession session) {
        final Dispatcher removed = dispatchers.remove(name);
        if (null == removed) {
            return null;
        }
        
        if(removed.session != session) {
            // name 相同， 但是并不是同一个 session 实例，
            // 还得再放回去，避免误删
            dispatchers.put(name, removed);
        } else {
            inboundSessionCounter.dec();
            eventBus.post(new RtspSessionRemovedEvent(numSessions(), session));
            removed.closeAll();
        }

        return removed.session;
    }

    public Dispatcher putSession(final String name,
                              final RtspSession session) {
        final Dispatcher removed = dispatchers.put(name, new Dispatcher(session));
        if (null != removed) {
            inboundSessionCounter.dec();
            eventBus.post(new RtspSessionRemovedEvent(numSessions(), removed.session));
            removed.closeAll();
        }

        inboundSessionCounter.inc();
        eventBus.post(new RtspSessionUpdatedEvent(numSessions(), session));
        logger.info("{} device session(s) online", numSessions());
        
        
        return dispatchers.get(name);
    }

    public int register(String name,
                        RtspSessionListener newItem) {
        if (null != newItem) {
            Dispatcher element = dispatchers.get(name);

            if (null != element) {
                element.addRtspSessionListener(newItem);
                return element.numListeners();
            }

        }
        return 0;
    }

    public void unregister(String name,
                           RtspSessionListener listener) {
        if (null != listener) {
            Dispatcher element = dispatchers.get(name);

            if (null != element) {
                element.removeRtspSessionListener(listener);
            }
        }
    }


    public Collection<String> sessionNames() {
        return dispatchers.keySet();
    }

    public int numSessions() {
        return dispatchers.size();
    }

    public SessionDescription getSessionDescription(String name) {
        Dispatcher dispatcher = dispatchers.get(name);

        return null != dispatcher ? dispatcher.getSessionDescription() : null;
    }


    public static class Dispatcher {
        final private RtspSession session;
        final private List<RtspSessionListener> listeners =
                new CopyOnWriteArrayList<RtspSessionListener>();


        public Dispatcher(RtspSession session) {
            super();
            this.session = session;
        }


        public int numListeners() {
            return listeners.size();
        }

        void addRtspSessionListener(RtspSessionListener listener) {
            listeners.remove(listener);
            listeners.add(listener);
        }

        void removeRtspSessionListener(RtspSessionListener listener) {
            listeners.remove(listener);
        }


        void closeAll() {
            for (RtspSessionListener rtspListener : listeners) {
                try {
                    rtspListener.close();
                } catch (Exception ex) {
                    // 独立 Listener 的异常不能传播到其他 listener
                    logger.warn("fail close {}", rtspListener);
                }
            }
            session.close();
            listeners.clear();
        }

        public <T> void dispatch(RtpEvent<T> event) {
            for (RtspSessionListener rtspListener : listeners) {
                try {
                    rtspListener.on(event);
                } catch (Exception ex) {
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

    public boolean usedMetricRegistry() {
        return usedMetricRegistry;
    }

    
    public ChannelHandler channelInboundMeterHandler() {
        if (!usedMetricRegistry()) {
            throw new IllegalAccessError("can't use metric registry, please call withMetricRegistry(name, registry) first");
        }

        return new ChannelInboundHandlerAdapter() {
            @Override
            public void channelActive(ChannelHandlerContext ctx) throws Exception {
                ctx.fireChannelActive();

                // 新来一个连接
                channelsCounter.inc();
            }

            @Override
            public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                ctx.fireChannelInactive();

                // 断开了一个连接
                channelsCounter.dec();
            }

            @Override
            public void channelRead(ChannelHandlerContext ctx,
                                    Object msg) throws Exception {
                final ByteBuf buf = (ByteBuf) msg;
                final int readerIndex = buf.readerIndex();

                ctx.fireChannelRead(msg);

                // 统计输入流量
                inboundIoMeter.mark(buf.readerIndex() - readerIndex);
            }
        };
    }
    
    public ChannelHandler channelOutboundMeterHandler() {
        if (!usedMetricRegistry()) {
            throw new IllegalAccessError("can't use metric registry, please call withMetricRegistry(name, registry) first");
        }
        
        return new ChannelOutboundHandlerAdapter() {
            @Override
            public void write(ChannelHandlerContext ctx,
                              Object msg,
                              ChannelPromise promise) throws Exception {
                final ByteBuf buf = (ByteBuf) msg;
                final int writableBytes = buf.readableBytes();

                ctx.write(msg, promise);

                outboundIoMeter.mark(writableBytes);
            }
        };
    }
}
