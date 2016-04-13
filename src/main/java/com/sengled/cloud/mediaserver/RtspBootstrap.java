package com.sengled.cloud.mediaserver;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.rtsp.RtspResponseEncoder;

import java.util.Map.Entry;

import org.slf4j.LoggerFactory;

import com.sengled.cloud.mediaserver.rtsp.codec.RtspInterleavedFrameEncoder;
import com.sengled.cloud.mediaserver.rtsp.codec.RtspObjectAggregator;
import com.sengled.cloud.mediaserver.rtsp.codec.RtspRequestDecoder;

public class RtspBootstrap {
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(RtspBootstrap.class);
    
    final private PooledByteBufAllocator allocator;

    final private ServerBootstrap bootstrap;
    
    private ChannelGroup channels = new DefaultChannelGroup("rtsp-server", null);
    
    private Class<? extends ChannelHandler> rtspHandlerClass;
    
    public RtspBootstrap() {
        this(false);
    }
    
    public RtspBootstrap(boolean preferDirect) {
        this.bootstrap = makeServerBosststrap();
        this.allocator = new PooledByteBufAllocator(preferDirect);
    }
    
    public RtspBootstrap withHandlerClass(Class<? extends ChannelHandler> rtspHandlerClass) {
        this.rtspHandlerClass = rtspHandlerClass;
        
        return this;
    }
    
    public void listen(int port) throws InterruptedException {
        ensureHandlerOK();
        
        ChannelFuture future = bootstrap.bind(port).sync();
        Channel channel = future.channel();

        channels.add(channel);
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

    public void listen(int port,
                       String host) throws InterruptedException {
        ensureHandlerOK();
        
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

    private ServerBootstrap makeServerBosststrap() {
        int numCpu = Runtime.getRuntime().availableProcessors();
        
        ServerBootstrap b = new ServerBootstrap();
   
        NioEventLoopGroup bossGroup = new NioEventLoopGroup(Math.max(1, numCpu * 2));
        NioEventLoopGroup workerGroup = new NioEventLoopGroup();
        b.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    public void initChannel(SocketChannel ch) throws Exception {
                        // server端发送的是httpResponse，所以要使用HttpResponseEncoder进行编码
                        ch.pipeline().addLast("rtspEncoder", new RtspResponseEncoder());
                        ch.pipeline().addLast("FrameEncoder", new RtspInterleavedFrameEncoder());
                        
                        ch.pipeline().addLast(new MessageToMessageEncoder<FullHttpResponse>() {
                            protected void encode(ChannelHandlerContext ctx, FullHttpResponse resp, java.util.List<Object> out) throws Exception {
                                out.add(resp.retain());
                                HttpHeaders headers = resp.headers();
                                if (logger.isInfoEnabled()) {
                                    logger.info("--------------- response --------------");
                                    for (Entry<String, String> entry : headers) {
                                        logger.info("{}:{}", entry.getKey(), entry.getValue());
                                    }
                                    logger.debug("<<<");
                                }
                            };
                        });
                        

                        // server端接收到的是httpRequest，所以要使用HttpRequestDecoder进行解码
                        ch.pipeline().addLast("rtspDecoder", new RtspRequestDecoder());
                        ch.pipeline().addLast("objectAggregator", new RtspObjectAggregator(64 * 1024));
                        ch.pipeline().addLast("rtsp", rtspHandlerClass.newInstance());
                    }
                }).option(ChannelOption.ALLOCATOR, allocator)
                .option(ChannelOption.SO_BACKLOG, 128)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childOption(ChannelOption.ALLOCATOR, allocator);

        return b;
    }
}
