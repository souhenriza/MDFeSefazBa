package com.souhenriza.sefazba.domain.exceptions;

public class BatchNotFoundException extends RuntimeException {

    public BatchNotFoundException(Integer id) {
        super("Lote de extração não encontrado: " + id);
    }

    public BatchNotFoundException() {
        super("Nenhum lote de extração interrompido ou com erros para retomar.");
    }
}
