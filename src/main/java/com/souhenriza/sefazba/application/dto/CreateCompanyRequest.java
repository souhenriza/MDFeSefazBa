package com.souhenriza.sefazba.application.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// Older company JSON files still carry the removed responsibleUserId
@JsonIgnoreProperties({"responsibleUserId"})
public record CreateCompanyRequest(
        String legalName,
        String tradeName,
        String cnpj,
        int dominioSistemasId,
        String sefazLogin,
        String sefazPassword,
        boolean commercialIntention,
        String preferOutputPath
) {}
