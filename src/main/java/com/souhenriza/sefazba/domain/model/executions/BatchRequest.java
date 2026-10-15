package com.souhenriza.sefazba.domain.model.executions;

import com.souhenriza.sefazba.shared.enums.ReportFormat;

import java.time.LocalDate;

/* What was asked when the batch was created. Persisted with the batch so a resume repeats the same request. */
public record BatchRequest(
        BatchType type,
        String description,
        String periodLabel,
        LocalDate startDate,
        LocalDate endDate,
        boolean genReport,
        ReportFormat reportFormat,
        String outputPath
) {
}
