package com.souhenriza.sefazba.infrastructure.persistence.extracts;

import com.souhenriza.sefazba.infrastructure.persistence.Jpa;
import com.souhenriza.sefazba.infrastructure.persistence.JpaRepository;

import java.util.List;

public class ExtractRepository extends JpaRepository<ExtractResultEntity, Integer> {

    public ExtractRepository(Jpa jpa) {
        super(jpa, ExtractResultEntity.class, ExtractResultEntity::getId);
    }

    public List<ExtractResultEntity> findAllByOrderByExtractedAtDesc(int limit) {
        return jpa.read(em -> em.createQuery(
                        "SELECT e FROM ExtractResultEntity e ORDER BY e.extractedAt DESC", ExtractResultEntity.class)
                .setMaxResults(limit)
                .getResultList());
    }
}
