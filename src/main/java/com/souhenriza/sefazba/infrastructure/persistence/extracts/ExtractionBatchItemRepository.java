package com.souhenriza.sefazba.infrastructure.persistence.extracts;

import com.souhenriza.sefazba.domain.model.executions.BatchItemStatus;
import com.souhenriza.sefazba.infrastructure.persistence.Jpa;
import com.souhenriza.sefazba.infrastructure.persistence.JpaRepository;

import java.util.Collection;
import java.util.List;

public class ExtractionBatchItemRepository extends JpaRepository<ExtractionBatchItemEntity, Integer> {

    public ExtractionBatchItemRepository(Jpa jpa) {
        super(jpa, ExtractionBatchItemEntity.class, ExtractionBatchItemEntity::getId);
    }

    public List<ExtractionBatchItemEntity> findByBatchIdOrderByIdAsc(Integer batchId) {
        return jpa.read(em -> em.createQuery(
                        "SELECT i FROM ExtractionBatchItemEntity i WHERE i.batchId = :batchId ORDER BY i.id",
                        ExtractionBatchItemEntity.class)
                .setParameter("batchId", batchId)
                .getResultList());
    }

    public List<ExtractionBatchItemEntity> findByBatchIdAndStatusInOrderByIdAsc(Integer batchId,
                                                                               Collection<BatchItemStatus> statuses) {
        return jpa.read(em -> em.createQuery(
                        "SELECT i FROM ExtractionBatchItemEntity i WHERE i.batchId = :batchId AND i.status IN :statuses"
                                + " ORDER BY i.id",
                        ExtractionBatchItemEntity.class)
                .setParameter("batchId", batchId)
                .setParameter("statuses", statuses)
                .getResultList());
    }

    public List<ExtractionBatchItemEntity> findAllByStatus(BatchItemStatus status) {
        return jpa.read(em -> em.createQuery(
                        "SELECT i FROM ExtractionBatchItemEntity i WHERE i.status = :status", ExtractionBatchItemEntity.class)
                .setParameter("status", status)
                .getResultList());
    }

    public long countByBatchIdAndStatusNot(Integer batchId, BatchItemStatus status) {
        return jpa.read(em -> em.createQuery(
                        "SELECT COUNT(i) FROM ExtractionBatchItemEntity i WHERE i.batchId = :batchId AND i.status <> :status",
                        Long.class)
                .setParameter("batchId", batchId)
                .setParameter("status", status)
                .getSingleResult());
    }
}
