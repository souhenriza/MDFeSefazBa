package com.souhenriza.sefazba.infrastructure.persistence.company;

import com.souhenriza.sefazba.infrastructure.persistence.Jpa;
import com.souhenriza.sefazba.infrastructure.persistence.JpaRepository;

import java.util.List;
import java.util.Optional;

public class CompanyRepository extends JpaRepository<CompanyEntity, Integer> {

    public CompanyRepository(Jpa jpa) {
        super(jpa, CompanyEntity.class, CompanyEntity::getId);
    }

    public Optional<CompanyEntity> findByCnpj(String cnpj) {
        return jpa.read(em -> em.createQuery("SELECT c FROM CompanyEntity c WHERE c.cnpj = :cnpj", CompanyEntity.class)
                .setParameter("cnpj", cnpj)
                .getResultStream()
                .findFirst());
    }

    public Optional<CompanyEntity> findByDominioSistemasId(int id) {
        return jpa.read(em -> em.createQuery(
                        "SELECT c FROM CompanyEntity c WHERE c.dominioSistemasId = :id", CompanyEntity.class)
                .setParameter("id", id)
                .getResultStream()
                .findFirst());
    }

    public boolean existsByCnpj(String cnpj) {
        return findByCnpj(cnpj).isPresent();
    }

    public List<CompanyEntity> findAllIfHasCommercialIntention() {
        return jpa.read(em -> em.createQuery(
                        "SELECT c FROM CompanyEntity c WHERE c.commercialIntention = true ORDER BY c.id", CompanyEntity.class)
                .getResultList());
    }
}
