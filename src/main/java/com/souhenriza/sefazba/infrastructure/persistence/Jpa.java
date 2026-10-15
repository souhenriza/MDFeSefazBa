package com.souhenriza.sefazba.infrastructure.persistence;

import com.souhenriza.sefazba.infrastructure.persistence.company.CompanyEntity;
import com.souhenriza.sefazba.infrastructure.persistence.extracts.ExtractResultEntity;
import com.souhenriza.sefazba.infrastructure.persistence.extracts.ExtractionBatchEntity;
import com.souhenriza.sefazba.infrastructure.persistence.extracts.ExtractionBatchItemEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import org.flywaydb.core.Flyway;
import org.hibernate.jpa.HibernatePersistenceConfiguration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Bootstraps Hibernate without Spring and replaces @Transactional.
 * <p>
 * {@link #inTransaction} binds an EntityManager to the current thread; repository calls made inside it share that
 * EntityManager, so entities loaded there are managed and their changes are flushed on commit. Calls made outside
 * a transaction get a short-lived EntityManager of their own, and a write outside a transaction commits by itself,
 * which matches the REQUIRES_NEW behaviour the batch progress relied on.
 */
public class Jpa implements AutoCloseable {

    private static final ThreadLocal<EntityManager> CURRENT = new ThreadLocal<>();

    private final EntityManagerFactory factory;

    public Jpa(String url) {
        createParentDirectory(url);
        Flyway.configure()
                .dataSource(url, null, null)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        this.factory = new HibernatePersistenceConfiguration("sefazba")
                .managedClass(CompanyEntity.class)
                .managedClass(ExtractResultEntity.class)
                .managedClass(ExtractionBatchEntity.class)
                .managedClass(ExtractionBatchItemEntity.class)
                .jdbcUrl(url)
                .jdbcDriver("org.sqlite.JDBC")
                .property("hibernate.dialect", "org.hibernate.community.dialect.SQLiteDialect")
                // Same column naming Spring Boot applied: batchType -> batch_type
                .property("hibernate.physical_naming_strategy",
                        "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy")
                .property("hibernate.hbm2ddl.auto", "validate")
                .createEntityManagerFactory();
    }

    public <T> T inTransaction(Function<EntityManager, T> work) {
        EntityManager current = CURRENT.get();
        if (current != null) {
            return work.apply(current);
        }

        EntityManager em = factory.createEntityManager();
        EntityTransaction tx = em.getTransaction();
        CURRENT.set(em);
        try {
            tx.begin();
            T result = work.apply(em);
            tx.commit();
            return result;
        } catch (RuntimeException e) {
            if (tx.isActive()) {
                tx.rollback();
            }
            throw e;
        } finally {
            CURRENT.remove();
            em.close();
        }
    }

    public void runInTransaction(Consumer<EntityManager> work) {
        inTransaction(em -> {
            work.accept(em);
            return null;
        });
    }

    /**
     * Read-only work: reuses the thread's transaction when there is one, otherwise a throwaway EntityManager.
     */
    public <T> T read(Function<EntityManager, T> work) {
        EntityManager current = CURRENT.get();
        if (current != null) {
            return work.apply(current);
        }
        try (EntityManager em = factory.createEntityManager()) {
            return work.apply(em);
        }
    }

    @Override
    public void close() {
        factory.close();
    }

    private static void createParentDirectory(String url) {
        String prefix = "jdbc:sqlite:";
        if (!url.startsWith(prefix)) {
            return;
        }
        Path parent = Path.of(url.substring(prefix.length())).toAbsolutePath().getParent();
        try {
            Files.createDirectories(parent);
        } catch (Exception e) {
            throw new IllegalStateException("Could not create database directory " + parent, e);
        }
    }
}
