package com.souhenriza.sefazba.application.dto;

public record CreateCompanyRequest(
        String legalName,
        String tradeName,
        String cnpj,
        int dominioSistemasId,
        String sefazLogin,
        String sefazPassword,
        Long responsibleUserId,
        boolean commercialIntention,
        String preferOutputPath
) {}
