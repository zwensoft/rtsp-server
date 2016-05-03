package com.sengled.cloud.mediaserver.spring.monitor;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.Callable;

import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

import com.sengled.cloud.async.TimeoutExecutor;

/**
 * 检测系统运行状态
 * 
 * @author 陈修恒
 * @date 2016年5月3日
 */
public class OSMonitor  implements InitializingBean {
    /***
     * CPU 使用率
     */
    private double cpuUseRate = 0.0;
            

    private final TimeoutExecutor monitorTaskExecutor = new TimeoutExecutor("server-monitor");
    
    private final Callable<Boolean> task; 
    
    
    public OSMonitor() {
        task = new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                try {
                    cpuUseRate = CpuUseRate.calculate();
                } finally {
                    // 3s 后重新检测
                    monitorTaskExecutor.setTimeout(getTaskCallable(), 5 * 1000);
                }

                return null;
            }

        };
    }

    private Callable<Boolean> getTaskCallable() {
        return task;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        // 300毫秒后， 开始系统检测
        monitorTaskExecutor.setTimeout(task, 300);
    }

    public double getCpuUseRate() {
        return cpuUseRate;
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
}
