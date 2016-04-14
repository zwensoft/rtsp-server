package com.sengled.cloud.mediaserver.rtsp;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang.StringUtils;

public class Transport {
    public static final String RTP_AVP_TCP = "RTP/AVP/TCP";
    public static final String UNICAST = "unicast";
    public static final String INTERLEAVED = "interleaved";
    
    private String tranport = RTP_AVP_TCP;
    private String unicast = UNICAST;
    
    private Map<String, String> parameters = new HashMap<String, String>();
    
    public void setUnicast(String castMode) {
        this.unicast = castMode;
    }
    
    public void setTranport(String protocol) {
        this.tranport = protocol;
    }
    
    public String getTranport() {
        return tranport;
    }
    
    public String getUnicast() {
        return unicast;
    }
    
    /**
     * @return  null if Parameter Not Found
     */
    public int[] getInterleaved() {
        String v = this.parameters.get(INTERLEAVED);
        if (null == v) {
            return null;
        }
        
        String[] nums = StringUtils.split(v, '-');
        int[] values = new int[nums.length];
        for (int i = 0; i < values.length; i++) {
            values[i] = Integer.parseInt(nums[i]);
        }
        
        return values;
    }
    
    public void setParameter(String name, String value) {
        this.parameters.put(name, value);
    }
    
    public String getParameter(String name) {
        return this.parameters.get(name);
    }
    
    public static Transport parse(String transport) {
        if (null == transport) {
            throw new IllegalArgumentException("Transport parse NULL");
        }
        
        Transport t = new Transport();
        String[] splits = StringUtils.split(transport,';');
        
        t.tranport = splits[0];
        t.unicast = splits[1];
        for (int i = 0; i < splits.length; i++) {
            if (splits[i].contains("=")) {
                String[] keyValue = StringUtils.split(splits[i],'=');
                t.parameters.put(keyValue[0], keyValue[1]);
            }
        }
        
        return t;
    }
    
    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append(tranport);
        buf.append(";").append(unicast);
        
        String key;
        
        key = INTERLEAVED;
        if (parameters.containsKey(key)) {
            buf.append(";").append(key).append("=").append(parameters.get(key));
        }
        
        key = "mode";
        if (parameters.containsKey(key)) {
            buf.append(";").append(key).append("=").append(parameters.get(key));
        }
        
        return buf.toString();
    }
}
