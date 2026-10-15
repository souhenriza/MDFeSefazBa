package com.souhenriza.sefazba.domain.exceptions;

public class CaptchaSolveException extends RuntimeException {

    public CaptchaSolveException(String message) {
        super(message);
    }

    public CaptchaSolveException(String message, Throwable cause) {
        super(message, cause);
    }
}
