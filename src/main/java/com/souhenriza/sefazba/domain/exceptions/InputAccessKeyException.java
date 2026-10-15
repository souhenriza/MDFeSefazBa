package com.souhenriza.sefazba.domain.exceptions;

public class InputAccessKeyException extends RuntimeException {
    public InputAccessKeyException(String notAuthenticatedOrWrongPage) {
        super(notAuthenticatedOrWrongPage);
    }
}
