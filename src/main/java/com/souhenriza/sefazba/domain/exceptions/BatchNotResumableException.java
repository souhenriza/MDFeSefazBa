package com.souhenriza.sefazba.domain.exceptions;

import com.souhenriza.sefazba.domain.model.executions.BatchStatus;

public class BatchNotResumableException extends RuntimeException {

    public BatchNotResumableException(Integer id, BatchStatus status) {
        super("O lote " + id + " está com status " + status + " e não pode ser retomado.");
    }
}
