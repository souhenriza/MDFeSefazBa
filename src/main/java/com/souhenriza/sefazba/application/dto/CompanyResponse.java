package com.souhenriza.sefazba.application.dto;

import com.souhenriza.sefazba.infrastructure.persistence.company.CompanyEntity;

public record CompanyResponse(
        Integer id,
        String legalName,
        String tradeName,
        String cnpj,
        int dominioSistemasId,
        Long responsibleUserId,
        Boolean commercialIntention
) {
    public static CompanyResponse fromEntity(CompanyEntity entity) {
        return new CompanyResponse(
                entity.getId(),
                entity.getLegalName(),
                entity.getTradeName(),
                entity.getCnpj(),
                entity.getDominioSistemasId(),
                entity.getResponsibleUserId(),
                entity.isCommercialIntention()
        );
    }
}
