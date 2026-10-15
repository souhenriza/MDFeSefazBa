package com.souhenriza.sefazba.application.service;

import com.souhenriza.sefazba.application.dto.ExtractionBatchDetailResponse;
import com.souhenriza.sefazba.application.dto.ExtractionBatchSummaryResponse;
import com.souhenriza.sefazba.infrastructure.persistence.company.CompanyEntity;
import com.souhenriza.sefazba.domain.exceptions.BatchNotFoundException;
import com.souhenriza.sefazba.domain.model.executions.BatchItemStatus;
import com.souhenriza.sefazba.domain.model.executions.BatchRequest;
import com.souhenriza.sefazba.domain.model.executions.BatchStatus;
import com.souhenriza.sefazba.infrastructure.persistence.Jpa;
import com.souhenriza.sefazba.infrastructure.persistence.extracts.ExtractionBatchEntity;
import com.souhenriza.sefazba.infrastructure.persistence.extracts.ExtractionBatchItemEntity;
import com.souhenriza.sefazba.infrastructure.persistence.extracts.ExtractionBatchItemRepository;
import com.souhenriza.sefazba.infrastructure.persistence.extracts.ExtractionBatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Persists the progress of a batch extraction company by company. Every state change runs in its own
 * transaction, so the table reflects what was done even if the process dies mid-batch. Entities loaded inside
 * {@link Jpa#inTransaction} are managed, so setter calls there are written on commit.
 */
@Slf4j
@RequiredArgsConstructor
public class ExtractionBatchService {

    private static final int MAX_ERROR_LENGTH = 1000;
    private static final List<BatchItemStatus> RETRYABLE_STATUSES = List.of(BatchItemStatus.PENDING, BatchItemStatus.ERROR);
    private static final List<BatchStatus> RESUMABLE_STATUSES = List.of(BatchStatus.INTERRUPTED, BatchStatus.COMPLETED_WITH_ERRORS);

    private final ExtractionBatchRepository batchRepository;
    private final ExtractionBatchItemRepository itemRepository;
    private final Jpa jpa;

    public ExtractionBatchEntity createBatch(BatchRequest request, List<CompanyEntity> companies) {
        return jpa.inTransaction(em -> insertBatch(request, companies));
    }

    private ExtractionBatchEntity insertBatch(BatchRequest request, List<CompanyEntity> companies) {
        ExtractionBatchEntity batch = new ExtractionBatchEntity();
        batch.setBatchType(request.type());
        batch.setDescription(request.description());
        batch.setPeriodLabel(request.periodLabel());
        batch.setStartDate(request.startDate());
        batch.setEndDate(request.endDate());
        batch.setGenReport(request.genReport());
        batch.setReportFormat(request.reportFormat());
        batch.setOutputPath(request.outputPath());
        batch.setStatus(BatchStatus.RUNNING);
        batch.setStartedAt(LocalDateTime.now());
        batch = batchRepository.save(batch);

        for (CompanyEntity company : companies) {
            ExtractionBatchItemEntity item = new ExtractionBatchItemEntity();
            item.setBatchId(batch.getId());
            item.setCompanyId(company.getId());
            item.setCompanyName(company.getLegalName());
            item.setStatus(BatchItemStatus.PENDING);
            item.setAttempts(0);
            itemRepository.save(item);
        }

        log.info("Extraction batch {} created with {} companies | Period: {} to {}",
                batch.getId(), companies.size(), request.startDate(), request.endDate());
        return batch;
    }

    public void markRunning(Integer itemId) {
        jpa.runInTransaction(em -> {
            ExtractionBatchItemEntity item = getItem(itemId);
            item.setStatus(BatchItemStatus.RUNNING);
            item.setAttempts(item.getAttempts() + 1);
            item.setErrorMessage(null);
            item.setStartedAt(LocalDateTime.now());
            item.setFinishedAt(null);
        });
    }

    public void markSuccess(Integer itemId, List<String> reportPaths) {
        jpa.runInTransaction(em -> {
            ExtractionBatchItemEntity item = getItem(itemId);
            item.setStatus(BatchItemStatus.SUCCESS);
            item.setReportPaths(reportPaths == null || reportPaths.isEmpty() ? null : String.join(";", reportPaths));
            item.setFinishedAt(LocalDateTime.now());
        });
    }

    public void markError(Integer itemId, Exception error) {
        jpa.runInTransaction(em -> {
            ExtractionBatchItemEntity item = getItem(itemId);
            String message = error.getClass().getSimpleName() + ": " + error.getMessage();
            item.setStatus(BatchItemStatus.ERROR);
            item.setErrorMessage(message.length() > MAX_ERROR_LENGTH ? message.substring(0, MAX_ERROR_LENGTH) : message);
            item.setFinishedAt(LocalDateTime.now());
        });
    }

    public BatchStatus finishBatch(Integer batchId) {
        return jpa.inTransaction(em -> {
            ExtractionBatchEntity batch = getBatch(batchId);
            boolean allSucceeded = itemRepository.countByBatchIdAndStatusNot(batchId, BatchItemStatus.SUCCESS) == 0;
            batch.setStatus(allSucceeded ? BatchStatus.COMPLETED : BatchStatus.COMPLETED_WITH_ERRORS);
            batch.setFinishedAt(LocalDateTime.now());
            return batch.getStatus();
        });
    }

    public ExtractionBatchEntity reopen(Integer batchId) {
        return jpa.inTransaction(em -> {
            ExtractionBatchEntity batch = getBatch(batchId);
            batch.setStatus(BatchStatus.RUNNING);
            batch.setFinishedAt(null);
            return batch;
        });
    }

    /**
     * The worker died outside the per-company error handling: leave the batch resumable instead of stuck in RUNNING.
     */
    public void interrupt(Integer batchId) {
        jpa.runInTransaction(em -> {
            itemRepository.findByBatchIdAndStatusInOrderByIdAsc(batchId, List.of(BatchItemStatus.RUNNING))
                    .forEach(item -> item.setStatus(BatchItemStatus.PENDING));
            ExtractionBatchEntity batch = getBatch(batchId);
            batch.setStatus(BatchStatus.INTERRUPTED);
            batch.setFinishedAt(LocalDateTime.now());
        });
    }

    /**
     * Called while holding the extraction lock, so nothing else can be running: whatever is still marked RUNNING
     * belonged to a process that died.
     */
    public void recoverInterruptedBatches() {
        jpa.runInTransaction(em -> {
            List<ExtractionBatchItemEntity> staleItems = itemRepository.findAllByStatus(BatchItemStatus.RUNNING);
            staleItems.forEach(item -> item.setStatus(BatchItemStatus.PENDING));

            List<ExtractionBatchEntity> staleBatches = batchRepository.findAllByStatus(BatchStatus.RUNNING);
            staleBatches.forEach(batch -> batch.setStatus(BatchStatus.INTERRUPTED));

            if (!staleBatches.isEmpty()) {
                log.warn("{} extraction batch(es) were interrupted and can be resumed: {}", staleBatches.size(),
                        staleBatches.stream().map(ExtractionBatchEntity::getId).toList());
            }
        });
    }

    public ExtractionBatchEntity getBatch(Integer batchId) {
        return batchRepository.findById(batchId).orElseThrow(() -> new BatchNotFoundException(batchId));
    }

    public ExtractionBatchEntity getLatestResumableBatch() {
        return batchRepository.findFirstByStatusInOrderByStartedAtDesc(RESUMABLE_STATUSES)
                .orElseThrow(BatchNotFoundException::new);
    }

    public List<ExtractionBatchItemEntity> getRetryableItems(Integer batchId) {
        return itemRepository.findByBatchIdAndStatusInOrderByIdAsc(batchId, RETRYABLE_STATUSES);
    }

    public List<ExtractionBatchSummaryResponse> findRecent(int limit) {
        return batchRepository.findAllByOrderByStartedAtDesc(limit).stream()
                .map(batch -> ExtractionBatchSummaryResponse.of(batch, itemRepository.findByBatchIdOrderByIdAsc(batch.getId())))
                .toList();
    }

    public ExtractionBatchDetailResponse findDetail(Integer batchId) {
        ExtractionBatchEntity batch = getBatch(batchId);
        return ExtractionBatchDetailResponse.of(batch, itemRepository.findByBatchIdOrderByIdAsc(batchId));
    }

    private ExtractionBatchItemEntity getItem(Integer itemId) {
        return itemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalStateException("Extraction batch item not found: " + itemId));
    }
}
