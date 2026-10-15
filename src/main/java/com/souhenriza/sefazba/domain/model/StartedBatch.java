package com.souhenriza.sefazba.domain.model;

import java.time.LocalDateTime;

/* A batch accepted for background processing; its progress lives in the extraction_batch tables. */
public record StartedBatch(
        Integer batchId,
        String description,
        LocalDateTime requestedAt,
        int totalCompanies
) {
}
