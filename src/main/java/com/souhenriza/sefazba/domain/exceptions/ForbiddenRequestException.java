package com.souhenriza.sefazba.domain.exceptions;

public class ForbiddenRequestException extends RuntimeException {
    public ForbiddenRequestException() {super("The request return a forbidden response. Maybe IP is blocked or " +
            "mainitence.");
    }
}
