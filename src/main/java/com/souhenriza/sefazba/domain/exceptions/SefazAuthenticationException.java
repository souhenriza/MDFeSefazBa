package com.souhenriza.sefazba.domain.exceptions;

public class SefazAuthenticationException extends RuntimeException {

    public SefazAuthenticationException(String message) {
        super(message);
    }

    public SefazAuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}
