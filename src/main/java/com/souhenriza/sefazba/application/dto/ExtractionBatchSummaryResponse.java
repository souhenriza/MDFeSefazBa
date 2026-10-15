package com.souhenriza.sefazba.application.dto;

import com.souhenriza.sefazba.domain.model.executions.BatchItemStatus;
import com.souhenriza.sefazba.domain.model.executions.BatchStatus;
import com.souhenriza.sefazba.domain.model.executions.BatchType;
import com.souhenriza.sefazba.infrastructure.persistence.extracts.ExtractionBatchEntity;
import com.souhenriza.sefazba.infrastructure.persistence.extracts.ExtractionBatchItemEntity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record ExtractionBatchSummaryResponse(
        Integer id,
        BatchType batchType,
        String description,
        String periodLabel,
        LocalDate startDate,
        LocalDate endDate,
        BatchStatus status,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        int total,
        long success,
        long error,
        long pending
) {
    public static ExtractionBatchSummaryResponse of(ExtractionBatchEntity batch, List<ExtractionBatchItemEntity> items) {
        long success = items.stream().filter(item -> item.getStatus() == BatchItemStatus.SUCCESS).count();
        long error = items.stream().filter(item -> item.getStatus() == BatchItemStatus.ERROR).count();
        return new ExtractionBatchSummaryResponse(
                batch.getId(),
                batch.getBatchType(),
                batch.getDescription(),
                batch.getPeriodLabel(),
                batch.getStartDate(),
                batch.getEndDate(),
                batch.getStatus(),
                batch.getStartedAt(),
                batch.getFinishedAt(),
                items.size(),
                success,
                error,
                items.size() - success - error
        );
    }
}
