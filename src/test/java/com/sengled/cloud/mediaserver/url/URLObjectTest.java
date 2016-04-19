package com.sengled.cloud.mediaserver.url;

import java.net.MalformedURLException;

import junit.framework.TestCase;

import com.sengled.cloud.mediaserver.url.URLObject;

public class URLObjectTest extends TestCase {
    public static void testSimple() throws MalformedURLException {
        String url = "rtsp://user:password@127.0.0.1:5454/path/proxh";
        URLObject obj = new URLObject(url);

        assertEquals("rtsp", obj.getScheme());
        assertEquals("user", obj.getUser());
        assertEquals("password", obj.getPassword());
        assertEquals("127.0.0.1", obj.getHost());
        assertEquals(5454, obj.getPort());
        assertEquals("/path/proxh", obj.getUri());
    }

    public static void testDefaultPort() throws MalformedURLException {
        String url = "rtsp://user:password@127.0.0.1/path/proxh";
        URLObject obj = new URLObject(url);

        assertEquals(554, obj.getPort());
    }
    
    public static void testWithParams() throws MalformedURLException {
        String url = "rtsp://user:password@127.0.0.1/path/proxh?a=b";
        URLObject obj = new URLObject(url);

        assertEquals("/path/proxh", obj.getUri());
    }
}
