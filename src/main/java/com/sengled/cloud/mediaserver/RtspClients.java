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
import io.netty.handler.codec.rtsp.RtspRequestEncoder;
import io.netty.handler.timeout.IdleStateHandler;

import java.io.IOException;

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

    public static RtspClient open(String url) throws InterruptedException, IOException {
        return clients.doOpen(url, clients.workerGroup);
    }
    
    public RtspClient open(String url, EventLoopGroup workerGroup) throws InterruptedException, IOException {
        return doOpen(url, workerGroup);
    }

    private RtspClient doOpen(String url,
                              EventLoopGroup workerGroup) throws InterruptedException, IOException {
        URLObject urlObj = new URLObject(url);

        Bootstrap b = new Bootstrap();
        b.group(workerGroup);
        b.channel(NioSocketChannel.class);
        b.option(ChannelOption.SO_KEEPALIVE, true);
        b.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5 * 1000);
        b.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            public void initChannel(SocketChannel ch) throws Exception {
                ch.pipeline().addLast(new IdleStateHandler(60, 30, 0));
                ch.pipeline().addLast(new RtspRequestEncoder());
                ch.pipeline().addLast(new RtspResponseDecoder());
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
            RtspClient client = new RtspClient(urlObj, channel);
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
