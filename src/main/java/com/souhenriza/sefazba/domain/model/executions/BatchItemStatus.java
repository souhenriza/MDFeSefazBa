package com.souhenriza.sefazba.domain.model.executions;

import lombok.Getter;

@Getter
public enum BatchItemStatus {
    PENDING("Aguardando processamento. "),
    RUNNING("Em processamento. "),
    SUCCESS("Extração bem sucedida. "),
    ERROR("Consulte novamente. ");

    private final String description;

    BatchItemStatus(String description) {
        this.description = description;
    }
}
