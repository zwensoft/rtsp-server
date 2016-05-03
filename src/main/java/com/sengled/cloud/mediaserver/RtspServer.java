package com.sengled.cloud.mediaserver;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
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
public class RtspServer {
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(RtspServer.class);
    
    private static int port;
    public static int getPort() {
        return port;
    }
    
    final private PooledByteBufAllocator allocator;

    final private ServerBootstrap bootstrap;
    
    private ChannelGroup channels = new DefaultChannelGroup("rtsp-server", null);
    
    
    private NioEventLoopGroup bossGroup = new NioEventLoopGroup(1, new DefaultThreadFactory("netty-boss-group"));
    private NioEventLoopGroup workerGroup = new NioEventLoopGroup(Math.max(1, Runtime.getRuntime().availableProcessors() * 2), new DefaultThreadFactory("netty-worker-group"));
    
    private Class<? extends ChannelHandler> rtspHandlerClass;
    
    public RtspServer() {
        this(true);
    }
    
    public RtspServer(boolean preferDirect) {
        this.allocator = new PooledByteBufAllocator(preferDirect);
        this.rtspHandlerClass = RtspServerInboundHandler.class;
        this.bootstrap = makeServerBosststrap(this.allocator);
    }
    
    public RtspServer withHandlerClass(Class<? extends ChannelHandler> rtspHandlerClass) {
        this.rtspHandlerClass = rtspHandlerClass;
        
        return this;
    }
    
    public void listen(int port) throws InterruptedException {
        ensureHandlerOK();
        
        ChannelFuture future = bootstrap.bind(port).sync();
        Channel channel = future.channel();

        channels.add(channel);
        this.port = port;
        logger.info("listen: {}", channel.localAddress()); 
    }
    
    public void listen(int port,
                       String host) throws InterruptedException {
        ensureHandlerOK();
        
        ChannelFuture future = bootstrap.bind(host, port).sync();
        
        Channel channel = future.channel();
        channels.add(channel);
        this.port = port;
        logger.info("listen: {}", channel.localAddress()); 
    }
    
    private void ensureHandlerOK() {
        if (null == rtspHandlerClass) {
            throw new IllegalArgumentException("rtsp handler is UNKNOWN");
        }

        try{
            rtspHandlerClass.newInstance();
        } catch(Exception e) {
            throw new IllegalArgumentException("can't call new " + rtspHandlerClass + "()");
        }
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
         .childOption(ChannelOption.SO_RCVBUF, 4 * 1500)
         .childOption(ChannelOption.SO_SNDBUF, 8 * 1500)
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
                ch.pipeline().addLast("rtsp", rtspHandlerClass.newInstance());
            }
         });

        return b;
    }
}
