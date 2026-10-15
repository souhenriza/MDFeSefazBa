package com.souhenriza.sefazba.application.dto;

import com.souhenriza.sefazba.domain.model.executions.BatchItemStatus;
import com.souhenriza.sefazba.infrastructure.persistence.extracts.ExtractionBatchEntity;
import com.souhenriza.sefazba.infrastructure.persistence.extracts.ExtractionBatchItemEntity;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

public record ExtractionBatchDetailResponse(
        ExtractionBatchSummaryResponse summary,
        List<Item> items
) {
    public record Item(
            Integer id,
            Integer companyId,
            String companyName,
            BatchItemStatus status,
            Integer attempts,
            String errorMessage,
            List<String> reportPaths,
            LocalDateTime startedAt,
            LocalDateTime finishedAt
    ) {
        static Item fromEntity(ExtractionBatchItemEntity entity) {
            List<String> paths = entity.getReportPaths() == null
                    ? List.of()
                    : Arrays.asList(entity.getReportPaths().split(";"));
            return new Item(
                    entity.getId(),
                    entity.getCompanyId(),
                    entity.getCompanyName(),
                    entity.getStatus(),
                    entity.getAttempts(),
                    entity.getErrorMessage(),
                    paths,
                    entity.getStartedAt(),
                    entity.getFinishedAt()
            );
        }
    }

    public static ExtractionBatchDetailResponse of(ExtractionBatchEntity batch, List<ExtractionBatchItemEntity> items) {
        return new ExtractionBatchDetailResponse(
                ExtractionBatchSummaryResponse.of(batch, items),
                items.stream().map(Item::fromEntity).toList()
        );
    }
}
