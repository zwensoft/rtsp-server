package com.sengled.cloud.monitor;

import io.netty.util.internal.SystemPropertyUtil;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import com.sengled.cloud.async.TimerExecutor;
import com.sengled.cloud.monitor.VmstatCallable.VmstatInfo;

/**
 * 检测系统运行状态
 * 
 * @author 陈修恒
 * @date 2016年5月3日
 */
public class OSMonitor {
    private static final Logger logger = LoggerFactory.getLogger(OSMonitor.class);
    private static final String VMSTATA_US = "us";
    private static final String VMSTATA_SY = "sy";
    private static final String VMSTATA_ID = "id";
    private static final String VMSTATA_ST = "st";

    public static final boolean isWindows;
    static {
        isWindows = isWindows0();
    }
    
    private VmstatInfo vmstat = new VmstatInfo(new String[]{VMSTATA_US, VMSTATA_SY, VMSTATA_ID, VMSTATA_ST});
    private final TimerExecutor monitorTaskExecutor = new TimerExecutor("server-monitor");
    private final Callable<Boolean> task; 
    
    
    public OSMonitor() {
        task = new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                try {
                    if(!isWindows) {
                        vmstat = new VmstatCallable().call();
                    }
                } finally {
                    // 3s 后重新检测
                    monitorTaskExecutor.setTimeout(getTaskCallable(), 10 * 1000);
                }

                return null;
            }

        };
    }

    public OSMonitor withMetricRegistry(MetricRegistry registry) {
        // id, CPU 空闲
        registry.register(MetricRegistry.name(OSMonitor.class, VMSTATA_ID), new Gauge<Double>() {
            @Override
            public Double getValue() {
                return vmstat.getAverageValue(VMSTATA_ID);
            }
        });
        
        // us, 用户 CPU 使用率
        registry.register(MetricRegistry.name(OSMonitor.class, VMSTATA_US), new Gauge<Double>() {
            @Override
            public Double getValue() {
                return vmstat.getAverageValue(VMSTATA_US);
            }
        });

        // sy + st, 系统 CPU 使用率 + 虚拟机 CPU 使用率
        registry.register(MetricRegistry.name(OSMonitor.class, VMSTATA_SY + "," + VMSTATA_ST), new Gauge<Double>() {
            @Override
            public Double getValue() {
                return vmstat.getAverageValue(VMSTATA_SY) + vmstat.getAverageValue(VMSTATA_ST);
            }
        });
        
        // sy, 系统 CPU 使用率
        registry.register(MetricRegistry.name(OSMonitor.class, VMSTATA_SY), new Gauge<Double>() {
            @Override
            public Double getValue() {
                return vmstat.getAverageValue(VMSTATA_SY);
            }
        });
        
        // st, 虚拟机 CPU 使用率
        registry.register(MetricRegistry.name(OSMonitor.class, VMSTATA_ST), new Gauge<Double>() {
            @Override
            public Double getValue() {
                return vmstat.getAverageValue(VMSTATA_ST);
            }
        });
        
        // heap memory
        final MemoryMXBean osmb = ManagementFactory.getMemoryMXBean();
        registry.register(MetricRegistry.name(OSMonitor.class, "heapMemory"), new Gauge<Long>() {
            @Override
            public Long getValue() {
                return osmb.getHeapMemoryUsage().getUsed();
            }
        });
        
        // non head memory
        registry.register(MetricRegistry.name(OSMonitor.class, "nonHeapMemory"), new Gauge<Long>() {
            @Override
            public Long getValue() {
                return osmb.getNonHeapMemoryUsage().getUsed();
            }
        });
        
        return this;
    }
    

    public void start() {
        // 300毫秒后， 开始系统检测
        monitorTaskExecutor.setTimeout(task, 300);
    }
    

    
    public List<String> getLocalIPList(){
        List<String> ipList = new ArrayList<String>();
        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            NetworkInterface networkInterface;
            Enumeration<InetAddress> inetAddresses;
            InetAddress inetAddress;
            String ip;
            while (networkInterfaces.hasMoreElements()) {
                networkInterface = networkInterfaces.nextElement();
                inetAddresses = networkInterface.getInetAddresses();
                while (inetAddresses.hasMoreElements()) {
                    inetAddress = inetAddresses.nextElement();
                    if (inetAddress != null && inetAddress instanceof Inet4Address) { // IPV4
                        ip = inetAddress.getHostAddress();
                        ipList.add(ip);
                    }
                }
            }
        } catch (SocketException e) {
            LoggerFactory.getLogger(getClass()).warn("获取本机 IP 列表出错!", e);
        }
        
        return ipList;
    }
    
    public double getCpuIdRate() {
        return vmstat.getAverageValue(VMSTATA_ID);
    }
    
    private Callable<Boolean> getTaskCallable() {
        return task;
    }
    
    
    private static boolean isWindows0() {
        boolean windows = SystemPropertyUtil.get("os.name", "").toLowerCase(Locale.US).contains("win");
        if (windows) {
            logger.debug("Platform: Windows");
        }
        return windows;
    }
}
