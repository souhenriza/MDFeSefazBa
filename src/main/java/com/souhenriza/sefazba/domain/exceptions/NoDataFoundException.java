package com.souhenriza.sefazba.domain.exceptions;

public class NoDataFoundException extends RuntimeException {

    public NoDataFoundException() {
        super("A consulta não retornou dados para o período informado.");
    }
}
