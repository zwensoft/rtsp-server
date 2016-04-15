package com.sengled.cloud.mediaserver.url;

import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class URLObject {
    private final String scheme;
    private final String user;
    private final String password;
    private final String host;
    private final int port;
    private final String uri;

    public URLObject(String url) {
        Pattern pattern = Pattern.compile("^([^:]+)://(([^:]+):([^@]*)@)?([^:/]+)(:([0-9]+))?([^\\?]*)");
        Matcher m = pattern.matcher(url);
        if (!m.find()) {
            throw new IllegalArgumentException("非法的 RTSP 地址[" + url + "]");
        }

        scheme = m.group(1);
        user = m.group(3);
        password = m.group(4);
        host = m.group(5);

        int defaultPort = 80;
        if (scheme.equals("rtsp")) {
            defaultPort = 554;
        } else if (scheme.equals("rtmp")) {
            defaultPort = 1935;
        } else if (scheme.equals("https")) {
            defaultPort = 443;
        }
        
        String portString = m.group(7);
        try {
            if (null != portString) {
                defaultPort = Integer.parseInt(portString);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("'" + portString + "' is NOT a port value");
        }

        port = defaultPort;
        uri = m.group(8);
        
    }

    public String getScheme() {
        return scheme;
    }
    
    public String getHost() {
        return host;
    }

    public String getPassword() {
        return password;
    }

    public int getPort() {
        return port;
    }

    public String getUri() {
        return uri;
    }
    public String getUrl() {
        return scheme + "://" + host + ":" + port + uri;
    }
    
    public String getUrl(String uri) {
        if (null != uri) {
            return scheme + "://" + host + ":" + port + uri;
        } else {
            return null;
        }
    }

    public String getUser() {
        return user;
    }
    
    @Override
    public String toString() {
        return getUrl();
    }
    
}
