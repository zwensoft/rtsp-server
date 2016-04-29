package com.sengled.cloud.mediaserver;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.rtsp.RtspEncoder;
import io.netty.handler.timeout.IdleStateHandler;

import java.io.IOException;

import org.apache.commons.lang.StringUtils;

import com.sengled.cloud.mediaserver.rtsp.codec.RtspObjectDecoder;
import com.sengled.cloud.mediaserver.rtsp.codec.RtspResponseDecoder;
import com.sengled.cloud.mediaserver.url.URLObject;

public class RtspClients {
    private static RtspClients clients;
    static {
        clients = new RtspClients();
    }
    
    private EventLoopGroup workerGroup = new NioEventLoopGroup(Runtime.getRuntime().availableProcessors() * 2 + 1);
    private RtspClients() {
        
    }

    public static RtspClient open(String url, String name) throws InterruptedException, IOException {
        return clients.doOpen(new URLObject(url), name);
    }
    
    public static RtspClient open(URLObject urlObj, String name) throws InterruptedException, IOException {
        return clients.doOpen(urlObj, name);
    }
    
    public RtspClient open(String url, String name, EventLoopGroup workerGroup) throws InterruptedException, IOException {
        return doOpen(new URLObject(url), name);
    }

    private RtspClient doOpen(URLObject urlObj,
    						  String name) throws InterruptedException, IOException {
        if (StringUtils.isEmpty(name)) {
        	throw new IllegalArgumentException("stream name is EMPTY");
        } else if(!StringUtils.startsWith(name, "/") ) {
        	throw new IllegalArgumentException("stream name[" + name + "] is NOT start with '/'");
        }
        


        Bootstrap b = new Bootstrap();
        b.group(workerGroup)
         .channel(NioSocketChannel.class)
         .option(ChannelOption.SO_KEEPALIVE, true)
         .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5 * 1000)
         .option(ChannelOption.SO_RCVBUF, 32 * 1024)
         .handler(new ChannelInitializer<SocketChannel>() {
            @Override
            public void initChannel(SocketChannel ch) throws Exception {
                ch.pipeline().addLast(new IdleStateHandler(60, 30, 0));
                ch.pipeline().addLast(new RtspEncoder());
                ch.pipeline().addLast(RtspObjectDecoder.NAME, new RtspResponseDecoder());
            }
        });

        // Start the client.
        ChannelFuture f = b.connect(urlObj.getHost(), urlObj.getPort());
        Channel channel = null;
        try {
            channel = f.sync().channel();
        } catch (InterruptedException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
        

        boolean closeChannel = true;
        try {
            RtspClient client = new RtspClient(name, urlObj, channel);
            channel.pipeline().addLast(client.getRtspResponseHandler());
            client.connect();
            
            closeChannel = false;
            return client;
        } finally {
            if (closeChannel) {
                channel.close();
            }
        }
    }
}
