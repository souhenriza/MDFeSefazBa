package com.souhenriza.sefazba.domain.exceptions;

public class ExtractionInProgressException extends RuntimeException {

    public ExtractionInProgressException() {
        super("An extraction is already in progress. Please wait and try again.");
    }
}
