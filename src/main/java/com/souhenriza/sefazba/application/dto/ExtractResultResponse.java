package com.souhenriza.sefazba.application.dto;

import com.souhenriza.sefazba.domain.model.executions.ExtractStatus;
import com.souhenriza.sefazba.infrastructure.persistence.extracts.ExtractResultEntity;

import java.time.LocalDateTime;

public record ExtractResultResponse(
        Integer id,
        String company,
        String period,
        LocalDateTime extractedAt,
        String document,
        ExtractStatus extractStatus
) {
    public static ExtractResultResponse fromEntity(ExtractResultEntity entity) {
        return new ExtractResultResponse(
                entity.getId(),
                entity.getCompany(),
                entity.getPeriod(),
                entity.getExtractedAt(),
                entity.getDocument(),
                entity.getExtractStatus()
        );
    }
}
