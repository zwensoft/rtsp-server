package com.sengled.cloud.mediaserver.spring.reports;

import java.net.InetAddress;
import java.util.Collection;
import java.util.HashMap;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisHashCommands;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.eventbus.Subscribe;
import com.sengled.cloud.async.TimeoutExecutor;
import com.sengled.cloud.mediaserver.RtspServer;
import com.sengled.cloud.mediaserver.rtsp.RtspSession;
import com.sengled.cloud.mediaserver.rtsp.RtspSessions;
import com.sengled.cloud.mediaserver.rtsp.event.RtspSessionRemovedEvent;
import com.sengled.cloud.mediaserver.rtsp.event.RtspSessionUpdatedEvent;
import com.sengled.cloud.mediaserver.spring.monitor.OSMonitor;

/**
 * 把 {@link RtspSession} 信息保存到 redis 中
 * 
 * @author 陈修恒
 * @date 2016年5月3日
 */
public class RstpSessionRedisLogger implements InitializingBean {


    private static final Logger logger = LoggerFactory.getLogger(RstpSessionRedisLogger.class);

    /** 用于执行 redis 任务 */
    private final TimeoutExecutor redisTaskExecutor = new TimeoutExecutor("redis-updater");


    private StringRedisTemplate redisTemplate;
    private OSMonitor osMonitor;

    private long serverHeartBeat = 5 * 1000;
    private long sessionHeartBeat = 3 * 1000;
    private String innerHost;
    private String outerHost;

    private byte[] KEY_MEDIA_LIST;
    private byte[] KEY_MEDIA_INFO;
    private byte[] KEY_MEDIA_DEVICES;

    public RstpSessionRedisLogger() {
        RtspSessions.getInstance().sessionEventBus().register(this);
    }
    
    @Override
    public void afterPropertiesSet() throws Exception {
        if (null == innerHost) {
            innerHost = InetAddress.getLocalHost().getHostAddress();
        } else if (!osMonitor.getLocalIPList().contains(innerHost)) {
            String illegalIp = innerHost;
            innerHost = InetAddress.getLocalHost().getHostAddress();
            logger.warn("'{}' NOT local ip, use '{}' instead.", illegalIp, innerHost);
        }
        
        if (null == outerHost) {
            outerHost = InetAddress.getLocalHost().getHostName();
        }

        logger.warn("inner host is '{}'.", innerHost);
        logger.warn("outer host is '{}'.", outerHost);
        logger.warn("rtsp port is '{}'.", RtspServer.getInstance().getPort());

        KEY_MEDIA_LIST = "resource:media:list".getBytes();
        KEY_MEDIA_INFO = ("resource:media:" + innerHost + ":info").getBytes();
        KEY_MEDIA_DEVICES = ("resource:media:" + innerHost + ":devices").getBytes();

        // 服务器心跳
        redisTaskExecutor.setInterval(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                try {
                    updateServerHeartBeat();
                } catch (Exception ex) {
                    logger.error("Failed to send rtsp server heart beat");
                    logger.debug(ex.getMessage(), ex);
                }
                return null;
            }

        }, 0, serverHeartBeat);


        // 更新 session 列表
        redisTaskExecutor.setInterval(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                try{
                    updateSessionList();
                } catch(Exception ex){
                    logger.error("Failed to update device list to redis");
                    logger.debug(ex.getMessage(), ex);
                } finally {
                    logger.info("{} device session online", RtspSessions.getInstance().numSessions());
                }
                return null;
            }

        }, 0, sessionHeartBeat);
        
    }


    @Subscribe
    public void onSessionCreated(final RtspSessionUpdatedEvent event) {
        redisTaskExecutor.setTimeout(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                final RtspSession session = event.getSession();
                final String token = getDeviceToken(session.getName());

                redisTemplate.execute(new RedisCallback<Void>() {
                    @Override
                    public Void doInRedis(RedisConnection connection) throws DataAccessException {
                        addDeviceToken(connection, token);
                        return null;
                    }
                });
                return null;
            }
        }, 0);
        
    }

    @Subscribe
    public void onSessionRemoved(final RtspSessionRemovedEvent event) {
        redisTaskExecutor.setTimeout(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                final RtspSession session = event.getSession();
                final String token = getDeviceToken(session.getName());

                redisTemplate.execute(new RedisCallback<Void>() {
                    @Override
                    public Void doInRedis(RedisConnection connection) throws DataAccessException {
                        removeDeviceToken(connection, token);
                        return null;
                    }

                });
                return null;
            }
        }, 0);
        
    }


    private void addDeviceToken(RedisConnection connection,
                                final String token) {
        boolean success = connection.sAdd(KEY_MEDIA_DEVICES, token.getBytes());
        if (success) {
            logger.info("add session: '{}'", token);
        }

        byte[] key_token = getDeviceInfoKey(token);
        byte[] filed = "media".getBytes();
        HashMap<byte[], byte[]> tokenInfo = new HashMap<byte[], byte[]>();
        tokenInfo.put(filed, innerHost.getBytes());
        
        connection.expire(key_token, getDeviceInfoExpiredTime());
        connection.hMSet(key_token, tokenInfo);
    }


    private void removeDeviceToken(RedisConnection connection,
                                   final String token) {
        boolean success = connection.sRem(KEY_MEDIA_DEVICES, token.getBytes());
        if (success) {
            logger.info("removed session '{}' from {}", token, innerHost);
        }

        byte[] deviceInfoKey = getDeviceInfoKey(token);
        byte[] filed = "media".getBytes();
        byte[] values = connection.hGet(deviceInfoKey, filed);
        if (null != values && new String(values).equals(innerHost)) {
            connection.hDel(deviceInfoKey, filed);
        }
    }

    private String getDeviceToken(String name) {
        if (null == name || !name.endsWith(".sdp")) {
            return name;
        }

        if (name.startsWith("/")) {
            name = name.substring(1);
        }

        int lastIndex = name.lastIndexOf(".");
        return name.substring(0, lastIndex);
    }


    private byte[] getDeviceInfoKey(final String token) {
        return ("resource:device:" + token + ":info").getBytes();
    }


    protected void updateServerHeartBeat() {
        final double cpuUsageRate = Math.min(100, osMonitor.getCpuUseRate());

        final HashMap<byte[], byte[]> serverInfos;
        serverInfos = new HashMap<byte[], byte[]>();
        serverInfos.put("outer_ip".getBytes(), outerHost.getBytes());
        serverInfos.put("inner_ip".getBytes(), innerHost.getBytes());
        serverInfos.put("port".getBytes(), getRtspServerPort().getBytes());
        serverInfos.put("cur_conn".getBytes(),
                String.valueOf(RtspSessions.getInstance().sessionNames().size()).getBytes());

        redisTemplate.execute(new RedisCallback<Void>() {
            @Override
            public Void doInRedis(RedisConnection connection) throws DataAccessException {
                // 更新 media-list
                connection.zAdd(KEY_MEDIA_LIST, 100 - cpuUsageRate, innerHost.getBytes());
                logger.info("updated redis media list with innerHost = '{}'", innerHost);

                // 更新 media-info
                RedisHashCommands hashCmd = connection;
                hashCmd.hMSet(KEY_MEDIA_INFO, serverInfos);
                logger.info("updated redis media info with innerHost = '{}'", innerHost);

                return null;
            }
        });
    }


    private String getRtspServerPort() {
        return String.valueOf(RtspServer.getInstance().getPort());
    }

    protected void updateSessionList() {
        // get members from memory
        final Collection<String> memMembers;
        memMembers =
                Collections2.transform(RtspSessions.getInstance().sessionNames(),
                        new Function<String, String>() {
                            @Override
                            public String apply(String input) {
                                return getDeviceToken(input);
                            }

                        });


        redisTemplate.execute(new RedisCallback<Void>() {
            @Override
            public Void doInRedis(RedisConnection connection) throws DataAccessException {
                // 设置超时时间
                connection.expire(KEY_MEDIA_DEVICES, getDeviceInfoExpiredTime());

                // get members from redis
                Collection<String> redisMembers;
                redisMembers =
                        Collections2.transform(connection.sMembers(KEY_MEDIA_DEVICES),
                                new Function<byte[], String>() {
                                    @Override
                                    public String apply(byte[] input) {
                                        return new String(input);
                                    }
                                });


                // 内存中的 session， 都保存到 redis 中
                for (String newMember : memMembers) {
                    addDeviceToken(connection, newMember);
                }
                for (String oldMember : redisMembers) {
                    if (!memMembers.contains(oldMember)) {
                        removeDeviceToken(connection, oldMember);
                    }
                }

                return null;
            }
        });
    }
    
    private long getDeviceInfoExpiredTime() {
        long expireSeconds = sessionHeartBeat * 2 / 1000;
        
        return expireSeconds + 1;
    }


    public void setRedisTemplate(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void setInnerHost(String innerHost) {
        this.innerHost = innerHost;
    }

    public void setServerHeartBeat(long serverHeartBeat) {
        this.serverHeartBeat = serverHeartBeat;
    }

    public void setSessionHeartBeat(long sessionHeartBeat) {
        this.sessionHeartBeat = sessionHeartBeat;
    }

    public void setOuterHost(String outHost) {
        this.outerHost = outHost;
    }

    public void setOsMonitor(OSMonitor osMonitor) {
        this.osMonitor = osMonitor;
    }
}
