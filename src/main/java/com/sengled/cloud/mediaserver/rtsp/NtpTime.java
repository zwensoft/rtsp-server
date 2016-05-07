package com.sengled.cloud.mediaserver.rtsp;

import org.apache.commons.lang.time.DateFormatUtils;

/**
 * Ntp 时间
 * 
 * @author 陈修恒
 * @date 2016年4月29日
 */
public class NtpTime {
    private long ntpTs1;
    private long ntpTs2;
    private long rtpTime;
    
    
    
    public NtpTime(long ntpTs1, long ntpTs2, long rtpTime) {
        super();
        this.ntpTs1 = ntpTs1;
        this.ntpTs2 = ntpTs2;
        this.rtpTime = rtpTime;
    }

    public static long getNtpTime(long ntpTs1, long ntpTs2) {
        return (ntpTs1 - 2208988800L) * 1000 + ((ntpTs2 * 1000) >> 32);
    }
    
    public static long getNtpTs1(long ntpTimeMillis) {
    	return (ntpTimeMillis / 1000) + 2208988800L;
    }
    
    public static long getNtpTs2(long ntpTimeMillis) {
    	long mills = ntpTimeMillis % 1000;
    	
    	return 0xFFFFFFFF & ((mills << 32) / 1000);
    }
    
    public long getNtpTimeMillis() {
        return getNtpTime(ntpTs1, ntpTs2);
    }
    
    public long getNtpTs1() {
        return ntpTs1;
    }
    
    public long getNtpTs2() {
        return ntpTs2;
    }
    
    public long getRtpTime() {
        return rtpTime;
    }
    
    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append("{NtpTime");
        buf.append(", ntpTs1=").append(ntpTs1);
        buf.append(", ntp=").append(DateFormatUtils.format(getNtpTimeMillis(), "yyyy-MM-dd HH:mm:ss.SSS"));
        buf.append(", rtpTime=").append(rtpTime);
        buf.append("}");
        return buf.toString();
    }
}
