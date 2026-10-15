package com.souhenriza.sefazba.domain.model.company;

import com.souhenriza.sefazba.domain.model.InvalidCompanyException;
import com.souhenriza.sefazba.domain.model.sefaz.SefazCredentials;

public record Company(
        java.lang.String legalName,
        java.lang.String tradeName,
        String cnpj,
        int dominioSistemasId,
        SefazCredentials credentials,
        Long responsibleUserId,
        boolean haveCommercialIntention,
        String preferOutputPath
) {
    public Company {
        if (legalName == null || legalName.isBlank()) {
            throw new InvalidCompanyException("Legal name must not be blank");
        }
        if (cnpj == null) {
            throw new InvalidCompanyException("CNPJ must not be null");
        }
        if (credentials == null) {
            throw new InvalidCompanyException("Credentials must not be null");
        }
        if (responsibleUserId == null) {
            throw new InvalidCompanyException("Responsible user ID must not be null");
        }
    }
}
