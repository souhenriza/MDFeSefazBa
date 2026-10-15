package com.souhenriza.sefazba.domain.model.executions;

import lombok.Getter;

@Getter
public enum ExtractStatus {
    OK("Extração bem sucedida. "),
    ERROR("Consulte novamente. ");

    private final String description;

    ExtractStatus(String description) {
        this.description = description;
    }
}
