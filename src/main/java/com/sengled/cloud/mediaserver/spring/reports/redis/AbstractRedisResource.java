package com.sengled.cloud.mediaserver.spring.reports.redis;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.HashMap;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.sengled.cloud.async.TimerExecutor;
import com.sengled.cloud.mediaserver.rtsp.ServerContext;
import com.sengled.cloud.mediaserver.spring.monitor.OSMonitor;

/**
 * 每台服务器都是一个 Resource 实例
 * 
 * @author 陈修恒
 * @date 2016年5月5日
 */
public abstract class AbstractRedisResource {
    private static final Logger logger = LoggerFactory.getLogger(AbstractRedisResource.class);

    public static final String MEDIA = "media";
    public static final String TALKBACK = "talkback";


    final private String name;
    private String innerHost;
    private String outerHost;
    private int port;
    private ServerContext serverCtx;

    private OSMonitor osMonitor;
    private StringRedisTemplate redisTemplate;

    protected int updateResourceListDelay = 15;
    protected int updateResourceInfoDelay = 15;
    protected int updateResourceDevicesDelay = 15;

    protected AbstractRedisResource(String name) {
        this.name = name;
    }

    final public void start(int port, ServerContext rtspServer) throws UnknownHostException {
        this.port = port;
        this.serverCtx = rtspServer;

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

        logger.warn("{}-server {inner-host:'{}', outer-host:'{}', port:'{}'}",
                name,
                innerHost,
                outerHost,
                port);

        TimerExecutor timer = new TimerExecutor(name + "-timer-executor");
        timer.setInterval(updateResourceListCallable(), 0, updateResourceListDelay * 1000);
        timer.setInterval(updateResourceInfoCallable(), 0, updateResourceInfoDelay * 1000);
        timer.setInterval(updateResourceDevicesCallable(), 0, updateResourceDevicesDelay * 1000);
    }



    private Callable<Boolean> updateResourceListCallable() {
        return new Callable<Boolean>() {

            @Override
            public Boolean call() throws Exception {
                try {
                    updateResourceList();
                } catch (Exception ex) {
                    logger.warn("update {} failed", getListKey(), ex);
                }
                return null;
            }

        };
    }

    private Callable<Boolean> updateResourceInfoCallable() {
        return new Callable<Boolean>() {

            @Override
            public Boolean call() throws Exception {
                try {
                    updateResourceInfo();
                } catch (Exception ex) {
                    logger.warn("update {} failed", "resource:" + name + ":" + innerHost + ":info");
                }
                return null;
            }
        };
    }

    private Callable<Boolean> updateResourceDevicesCallable() {
        return new Callable<Boolean>() {

            @Override
            public Boolean call() throws Exception {
                try {
                    updateResourceDevices();
                } catch (Exception ex) {
                    logger.warn("update {} failed", "resource:" + name + ":" + innerHost
                            + ":devices");
                }
                return null;
            }

        };
    }

    protected void updateResourceList() {
        redisTemplate.execute(new RedisCallback<Void>() {
            @Override
            public Void doInRedis(RedisConnection connection) throws DataAccessException {
                double leftCpuUsageRate = 100 - osMonitor.getCpuUseRate();

                // 更新 media-list
                String key = getListKey();
                connection.zAdd(key.getBytes(), leftCpuUsageRate, innerHost.getBytes());
                logger.info("updated {}, score = {}, host = {}", key, leftCpuUsageRate, innerHost);

                return null;
            }

        });
    }


    private void updateResourceInfo() {
        final int numSessions = serverCtx.numSessions();

        final HashMap<byte[], byte[]> serverInfos;
        serverInfos = new HashMap<byte[], byte[]>();
        serverInfos.put("outer_ip".getBytes(), outerHost.getBytes());
        serverInfos.put("inner_ip".getBytes(), innerHost.getBytes());
        serverInfos.put("port".getBytes(), String.valueOf(port).getBytes());
        serverInfos.put("cur_conn".getBytes(), String.valueOf(numSessions).getBytes());


        redisTemplate.execute(new RedisCallback<Void>() {
            @Override
            public Void doInRedis(RedisConnection connection) throws DataAccessException {
                String key = getInfoKey();
                connection.hMSet(key.getBytes(), serverInfos);
                connection.expire(key.getBytes(), getExpireSeconds(updateResourceInfoDelay));
                logger.info("updated {}, {} session online", key, numSessions);
                return null;
            }


        });
    }

    private void updateResourceDevices() {
        final Collection<String> memMembers = getMembersFromMem();
        final String devicesKey = getDeviceListKey();


        redisTemplate.execute(new RedisCallback<Void>() {
            @Override
            public Void doInRedis(RedisConnection connection) throws DataAccessException {

                // get members from redis
                Collection<String> redisMembers = getMembersFromRedis(connection, devicesKey);

                // 内存中的 session， 都保存到 redis 中
                for (String newMember : memMembers) {
                    addDevice(connection, devicesKey, newMember);
                }
                for (String oldMember : redisMembers) {
                    if (!memMembers.contains(oldMember)) {
                        removeDevice(connection, devicesKey, oldMember);
                    }
                }

                // 设置超时时间
                connection.expire(devicesKey.getBytes(),
                        getExpireSeconds(updateResourceDevicesDelay));
                return null;
            }

        });
    }

    private Collection<String> getMembersFromMem() {
        final Collection<String> memMembers;
        memMembers =
                Collections2.transform(serverCtx.sessionNames(),
                        new Function<String, String>() {
                            @Override
                            public String apply(String input) {
                                return getDeviceToken(input);
                            }

                        });
        return memMembers;
    }

    private void addDevice(RedisConnection connection,
                           String deviceListKey,
                           final String newDevice) {
        // 更新 list 列表
        boolean success = connection.sAdd(deviceListKey.getBytes(), newDevice.getBytes());
        if (success) {
            logger.info("add session: '{}'", newDevice);
        }

        // 更新 device-info
        byte[] key_token = getDeviceInfoKey(newDevice).getBytes();
        connection.hSet(key_token, name.getBytes(), innerHost.getBytes());
    }

    private void removeDevice(RedisConnection connection,
                              String deviceListKey,
                              final String token) {
        // 更新 list 列表
        boolean success = connection.sRem(deviceListKey.getBytes(), token.getBytes());
        if (success) {
            logger.info("removed session '{}' from {}", token, innerHost);
        }

        // 更新 device-info
        byte[] deviceInfoKey = getDeviceInfoKey(token).getBytes();
        byte[] filed = name.getBytes();
        byte[] values = connection.hGet(deviceInfoKey, filed);
        if (null != values && new String(values).equals(innerHost)) {
            connection.hDel(deviceInfoKey, filed);
        }
    }

    private Collection<String> getMembersFromRedis(RedisConnection connection,
                                                   String key) {
        Collection<String> redisMembers;
        redisMembers =
                Collections2.transform(connection.sMembers(key.getBytes()),
                        new Function<byte[], String>() {
                            @Override
                            public String apply(byte[] input) {
                                return new String(input);
                            }
                        });
        return redisMembers;
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


    private long getExpireSeconds(int interval) {
        return interval + 1;
    }

    private String getListKey() {
        return "resource:" + name + ":list";
    }

    private String getInfoKey() {
        return "resource:" + name + ":" + innerHost + ":info";
    }

    protected String getDeviceListKey() {
        return "resource:" + name + ":" + innerHost + ":devices";
    }

    protected String getDeviceInfoKey(String token) {
        return "resource:device:" + token + ":info";
    }


    public void setInnerHost(String innerHost) {
        this.innerHost = innerHost;
    }

    public void setOuterHost(String outerHost) {
        this.outerHost = outerHost;
    }

    public void setOsMonitor(OSMonitor osMonitor) {
        this.osMonitor = osMonitor;
    }
    
    public void setRedisTemplate(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }
}
