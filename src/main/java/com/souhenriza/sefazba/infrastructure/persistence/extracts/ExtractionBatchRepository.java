package com.souhenriza.sefazba.infrastructure.persistence.extracts;

import com.souhenriza.sefazba.domain.model.executions.BatchStatus;
import com.souhenriza.sefazba.infrastructure.persistence.Jpa;
import com.souhenriza.sefazba.infrastructure.persistence.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public class ExtractionBatchRepository extends JpaRepository<ExtractionBatchEntity, Integer> {

    public ExtractionBatchRepository(Jpa jpa) {
        super(jpa, ExtractionBatchEntity.class, ExtractionBatchEntity::getId);
    }

    public List<ExtractionBatchEntity> findAllByOrderByStartedAtDesc(int limit) {
        return jpa.read(em -> em.createQuery(
                        "SELECT b FROM ExtractionBatchEntity b ORDER BY b.startedAt DESC", ExtractionBatchEntity.class)
                .setMaxResults(limit)
                .getResultList());
    }

    public List<ExtractionBatchEntity> findAllByStatus(BatchStatus status) {
        return jpa.read(em -> em.createQuery(
                        "SELECT b FROM ExtractionBatchEntity b WHERE b.status = :status", ExtractionBatchEntity.class)
                .setParameter("status", status)
                .getResultList());
    }

    public Optional<ExtractionBatchEntity> findFirstByStatusInOrderByStartedAtDesc(Collection<BatchStatus> statuses) {
        return jpa.read(em -> em.createQuery(
                        "SELECT b FROM ExtractionBatchEntity b WHERE b.status IN :statuses ORDER BY b.startedAt DESC",
                        ExtractionBatchEntity.class)
                .setParameter("statuses", statuses)
                .setMaxResults(1)
                .getResultStream()
                .findFirst());
    }
}
