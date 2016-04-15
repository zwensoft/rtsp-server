package com.sengled.cloud.mediaserver;

import java.io.IOException;

/**
 * 验证失败
 * 
 * @author 陈修恒
 * @date 2016年4月15日
 */
public class AuthorizedException  extends IOException {

    /** */
    private static final long serialVersionUID = -5988808023931086727L;

    public AuthorizedException(String name, String pass) {
        super("use auth[" + name + "," + pass + "]");
    }
    
    public AuthorizedException(String message, Throwable cause) {
        super(message, cause);
    }

    public AuthorizedException(String message) {
        super(message);
    }

    public AuthorizedException(Throwable cause) {
        super(cause);
    }

}
