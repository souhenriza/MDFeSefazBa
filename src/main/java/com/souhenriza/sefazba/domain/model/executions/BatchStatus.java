package com.souhenriza.sefazba.domain.model.executions;

import lombok.Getter;

@Getter
public enum BatchStatus {
    RUNNING("Lote em execução. "),
    COMPLETED("Todas as empresas foram extraídas com sucesso. "),
    COMPLETED_WITH_ERRORS("Lote finalizado com empresas em erro ou pendentes. "),
    INTERRUPTED("Lote interrompido antes de terminar. Retome-o. ");

    private final String description;

    BatchStatus(String description) {
        this.description = description;
    }

    public boolean isResumable() {
        return this == INTERRUPTED || this == COMPLETED_WITH_ERRORS;
    }
}
