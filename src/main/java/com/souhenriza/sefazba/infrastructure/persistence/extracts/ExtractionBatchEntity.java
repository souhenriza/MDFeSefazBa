package com.souhenriza.sefazba.infrastructure.persistence.extracts;

import com.souhenriza.sefazba.domain.model.executions.BatchStatus;
import com.souhenriza.sefazba.domain.model.executions.BatchType;
import com.souhenriza.sefazba.shared.enums.ReportFormat;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "extraction_batch")
@Getter
@Setter
@NoArgsConstructor
public class ExtractionBatchEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @Enumerated(EnumType.STRING)
    private BatchType batchType;
    private String periodLabel;
    private LocalDate startDate;
    private LocalDate endDate;
    @Enumerated(EnumType.STRING)
    private BatchStatus status;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String description;
    private boolean genReport;
    @Enumerated(EnumType.STRING)
    private ReportFormat reportFormat;
    private String outputPath;
}
