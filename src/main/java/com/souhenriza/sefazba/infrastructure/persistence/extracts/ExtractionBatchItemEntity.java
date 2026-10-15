package com.souhenriza.sefazba.infrastructure.persistence.extracts;

import com.souhenriza.sefazba.domain.model.executions.BatchItemStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "extraction_batch_item")
@Getter
@Setter
@NoArgsConstructor
public class ExtractionBatchItemEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private Integer batchId;
    private Integer companyId;
    private String companyName;
    @Enumerated(EnumType.STRING)
    private BatchItemStatus status;
    private Integer attempts;
    private String errorMessage;
    private String reportPaths;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
