package com.souhenriza.sefazba.domain.model.sefaz;

public record NFeEntry(
        String number,
        String cnpjEmitter,
        String companyName,
        String issueDate,
        String authorizationDate,
        String value,
        String accessKey,
        String emitterState,
        String status,
        String operationType
) {
    private static final String BAHIA_STATE = "BA";

    public boolean isFromBahia() {
        return BAHIA_STATE.equalsIgnoreCase(emitterState);
    }
}
