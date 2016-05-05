package com.sengled.cloud.mediaserver;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.rtsp.RtspEncoder;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.DefaultThreadFactory;

import org.slf4j.LoggerFactory;

import com.sengled.cloud.mediaserver.rtsp.RtspSession;
import com.sengled.cloud.mediaserver.rtsp.RtspSession.SessionMode;
import com.sengled.cloud.mediaserver.rtsp.ServerContext;
import com.sengled.cloud.mediaserver.rtsp.codec.RtpObjectAggregator;
import com.sengled.cloud.mediaserver.rtsp.codec.RtspObjectDecoder;
import com.sengled.cloud.mediaserver.rtsp.codec.RtspRequestDecoder;

/**
 * RTSP 服务
 * <p>
 * <ul>
 * <li>1、服务有 {@link SessionMode#PUBLISH PUBLISH}, {@link SessionMode#PLAY PLAY} 两种运用场景，
 *       分别用于：向服务器推流，和从播放器拉流。  
 * </li>
 * <li>2、利用 {@link RtspSession} 实现流内容共享。 服务器会把  {@link SessionMode#PUBLISH PUBLISH} 模式收取的流，转发给 {@link SessionMode#PLAY PLAY} 模式的客户端，</li>
 * 
 * </ul>
 * </p>
 * @author 陈修恒
 * @date 2016年4月15日
 */
public class RtspServerBootstrap {
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(RtspServerBootstrap.class);
    
    final private int port;
    final private ServerContext rtspServer;
    final private NioEventLoopGroup bossGroup;
    final private NioEventLoopGroup workerGroup;
    
    private ServerBootstrap bootstrap;
    private ChannelGroup channels = new DefaultChannelGroup("rtsp-server", null);
    
    
    public RtspServerBootstrap(String name, ServerContext rtspServer, int port) {
        int maxThreads = Math.max(1, Runtime.getRuntime().availableProcessors() * 2);

        this.port = port;
        this.rtspServer = rtspServer;
        this.bossGroup = new NioEventLoopGroup(1, new DefaultThreadFactory(name));
        this.workerGroup = new NioEventLoopGroup(maxThreads, new DefaultThreadFactory(name + "-worker"));
        this.bootstrap = makeServerBosststrap(new PooledByteBufAllocator(true));
    }
    
    
    public void start() throws InterruptedException {
        listen(getPort());
    }
    
    private void listen(int port) throws InterruptedException {
        listen(port, "0.0.0.0"); 
    }

    private void listen(int port, String host) throws InterruptedException {
        ChannelFuture future = bootstrap.bind(host, port).sync();
        
        Channel channel = future.channel();
        channels.add(channel);
        logger.info("listen: {}", channel.localAddress()); 
    }


    public void shutdown() {
        // close channel
        for (Channel channel : channels) {
            try {
                channel.close().sync().await();
                logger.info("closed {}", channel.localAddress());
            } catch (Exception e) {
                logger.warn("fail close {}", channel.localAddress());
            }
        }
        
        // shutdown threads
        bootstrap.group().shutdownGracefully();
        bootstrap.childGroup().shutdownGracefully();
    }

    private ServerBootstrap makeServerBosststrap(ByteBufAllocator allocator) {
        ServerBootstrap b = new ServerBootstrap();

        // server socket
        b.group(bossGroup, workerGroup);
        b.channel(NioServerSocketChannel.class);
        b.option(ChannelOption.ALLOCATOR, allocator);
        b.option(ChannelOption.SO_BACKLOG, 0); // 服务端处理线程全忙后，允许多少个新请求进入等待。 
        
        // accept socket
        b.childOption(ChannelOption.SO_KEEPALIVE, true)
         .childOption(ChannelOption.ALLOCATOR, allocator)
         .childOption(ChannelOption.SO_RCVBUF, 16 * 1500)
         .childOption(ChannelOption.SO_SNDBUF, 16 * 1500)
         .childOption(ChannelOption.SO_LINGER, 0)      // SO_LINGER还有一个作用就是用来减少TIME_WAIT套接字的数量
         .childOption(ChannelOption.TCP_NODELAY, true) // 禁用nagle算法，减少时延迟
         .childHandler(new ChannelInitializer<SocketChannel>() {
            @Override
            public void initChannel(SocketChannel ch) throws Exception {
                // 心跳
                ch.pipeline().addLast(new IdleStateHandler(0, 0, 60));
                ch.pipeline().addLast("rtspEncoder", new RtspEncoder());
        
                // server端接收到的是httpRequest，所以要使用HttpRequestDecoder进行解码
                ch.pipeline().addLast(RtspObjectDecoder.NAME, new RtspRequestDecoder());
                ch.pipeline().addLast("rtsp-channel-0", new RtpObjectAggregator(0));
                ch.pipeline().addLast("rtsp-channel-2", new RtpObjectAggregator(2));
                ch.pipeline().addLast("rtsp", new RtspServerInboundHandler(rtspServer));
            }
         });

        return b;
    }
    
    public int getPort() {
        if (port < 0) {
            throw new IllegalAccessError("Rtsp Server Port Not Init");
        }
        
        return port;
    }
}
