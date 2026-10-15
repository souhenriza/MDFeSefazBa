package com.souhenriza.sefazba.domain.exceptions;

public class InvalidUserException extends RuntimeException {
    public InvalidUserException() {super("O usuário ou a senha estão incorretos. Reveja os acessos.");
    }

    public InvalidUserException(String message){super(message);}
}
