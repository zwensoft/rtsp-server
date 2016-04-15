package com.sengled.cloud.mediaserver;

import java.io.IOException;

/**
 * 流不存在
 * 
 * @author 陈修恒
 * @date 2016年4月15日
 */
public class StreamNotFoundException extends IOException {

    /** */
    private static final long serialVersionUID = 9110767793340999081L;

    public StreamNotFoundException() {
        super();
    }

    public StreamNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    public StreamNotFoundException(String message) {
        super(message);
    }

    public StreamNotFoundException(Throwable cause) {
        super(cause);
    }

}
