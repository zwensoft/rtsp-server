package com.sengled.cloud.mediaserver.url;

import java.net.MalformedURLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * URL 对象
 * 
 * <p>
 * 把普通的 url 地址， 如 'rtsp://user:pass@host:port/uri' ，解析成方便使用的对象
 * @author 陈修恒
 * @date 2016年4月15日
 */
public class URLObject {
    private static final Logger logger = LoggerFactory.getLogger(URLObject.class);
    
    private final String scheme;
    private final String user;
    private final String password;
    private final String host;
    private final int port;
    private final String uri;

    
    private static Matcher match(String url) {
        Pattern pattern = Pattern.compile("^([^:]+)://(([^:]+):([^@]*)@)?([^:/]+)(:([0-9]+))?([^\\?]*)");
        Matcher m = pattern.matcher(url);
        return m;
    }

    

    public static String getServerUrl(String url) {
        if (null == url) {
            return null;
        }

        Matcher m =  match(url);
        if (!m.find()) {
            return "";
        }

        String scheme = m.group(1);
        String host = m.group(5);
        String portString = m.group(7);

        if (null == portString) {
            return scheme + "://" + host;
        } else {
            return scheme + "://" + host + ":" + portString;
        }
    }
    
    public static String getUri(String url) {
        if (null == url) {
            return null;
        }
        
        Matcher matcher =  match(url);
        if (matcher.find()) {
            return matcher.group(8);
        } else {
            return url;
        }
    }
    
    public URLObject(String url) throws MalformedURLException {
        Matcher m = match(url);
        if (!m.find()) {
            throw new MalformedURLException("非法的 RTSP 地址[" + url + "]");
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
