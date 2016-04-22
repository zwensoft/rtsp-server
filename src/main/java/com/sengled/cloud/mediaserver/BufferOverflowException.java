package com.sengled.cloud.mediaserver;

import java.io.IOException;

/**
 * The BufferOverflowException is used when the IO buffer's capacity has been
 * exceeded.
 *
 * @author 陈修恒
 * @date 2016年4月22日
 */
public class BufferOverflowException extends IOException {

    /** */
    private static final long serialVersionUID = -3737896064326962711L;

    public BufferOverflowException() {
        super();
    }

    public BufferOverflowException(String message, Throwable cause) {
        super(message, cause);
    }

    public BufferOverflowException(String message) {
        super(message);
    }

    public BufferOverflowException(Throwable cause) {
        super(cause);
    }

}
