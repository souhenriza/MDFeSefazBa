package com.souhenriza.sefazba.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * The subset of Spring Data's JpaRepository the services use, implemented on top of {@link Jpa}.
 */
public abstract class JpaRepository<E, ID> {

    protected final Jpa jpa;
    private final Class<E> type;
    private final Function<E, ID> idOf;

    protected JpaRepository(Jpa jpa, Class<E> type, Function<E, ID> idOf) {
        this.jpa = jpa;
        this.type = type;
        this.idOf = idOf;
    }

    public Optional<E> findById(ID id) {
        return jpa.read(em -> Optional.ofNullable(em.find(type, id)));
    }

    public List<E> findAll() {
        return jpa.read(em -> em.createQuery("SELECT e FROM " + type.getSimpleName() + " e ORDER BY e.id", type)
                .getResultList());
    }

    public boolean existsById(ID id) {
        return findById(id).isPresent();
    }

    public E save(E entity) {
        return jpa.inTransaction(em -> {
            if (idOf.apply(entity) == null) {
                em.persist(entity);
                return entity;
            }
            return em.merge(entity);
        });
    }

    public void deleteById(ID id) {
        jpa.runInTransaction(em -> {
            E entity = em.find(type, id);
            if (entity != null) {
                em.remove(entity);
            }
        });
    }
}
