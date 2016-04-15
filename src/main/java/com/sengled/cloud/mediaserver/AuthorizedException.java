package com.sengled.cloud.mediaserver;

public class AuthorizedException extends RuntimeException {

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
