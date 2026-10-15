package com.souhenriza.sefazba.application.dto;

public record UpdateCompanyRequest(
        String legalName,
        String tradeName,
        String cnpj,
        Integer dominioSistemasId,
        String sefazLogin,
        String sefazPassword,
        Long responsibleUserId,
        Boolean commercialIntention
) {}
