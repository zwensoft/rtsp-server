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
    private Rational rtpTimeUnit;
    
    
    
    public NtpTime(long ntpTs1, long ntpTs2, long rtpTime, Rational rtpTimeUnit) {
        super();
        this.ntpTs1 = ntpTs1;
        this.ntpTs2 = ntpTs2;
        this.rtpTime = rtpTime;
        this.rtpTimeUnit = rtpTimeUnit;
    }

    public static long getNtpTime(long ntpTs1, long ntpTs2) {
        return (ntpTs1 - 2208988800L) * 1000 + ((ntpTs2 * 1000) >> 32);
    }
    
    public long getNtpTimeMillis() {
        return getNtpTime(ntpTs1, ntpTs2);
    }
    
    public long getRtpTimeMillis() {
        return Rational.$_1_000.convert(rtpTime, rtpTimeUnit);
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

    public Rational getRtpTimeUnit() {
        return rtpTimeUnit;
    }
    
    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append("{NtpTime");
        buf.append(", ntpTs1=").append(ntpTs1);
        buf.append(", ntp=").append(DateFormatUtils.format(getNtpTimeMillis(), "yyyy-MM-dd HH:mm:ss.SSS"));
        buf.append(", rtp=").append(DateFormatUtils.format(getRtpTimeMillis(), "HH:mm:ss.SSS"));
        buf.append("}");
        return buf.toString();
    }
}
