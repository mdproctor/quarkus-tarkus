package io.quarkiverse.work.reports.api;

import java.util.Map;

import org.testcontainers.containers.PostgreSQLContainer;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

/**
 * Starts a real PostgreSQL container before Quarkus boots and injects a concrete JDBC URL.
 *
 * <p>
 * Flyway is disabled — Flyway migrations use H2-permissive SQL (e.g. bare "double" type)
 * that PostgreSQL rejects. Schema is created via Hibernate drop-and-create instead, which
 * uses correct PostgreSQL DDL derived from the entity annotations.
 *
 * <p>
 * This test resource must be paired with the postgres-dialect-test Surefire execution,
 * which sets quarkus.datasource.db-kind=postgresql as a system property BEFORE Quarkus
 * augmentation runs, so Agroal is configured with the PostgreSQL driver class.
 */
public class PostgresTestResource implements QuarkusTestResourceLifecycleManager {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Override
    public Map<String, String> start() {
        POSTGRES.start();
        return Map.of(
                "quarkus.datasource.db-kind", "postgresql",
                "quarkus.datasource.jdbc.url", POSTGRES.getJdbcUrl(),
                "quarkus.datasource.username", POSTGRES.getUsername(),
                "quarkus.datasource.password", POSTGRES.getPassword(),
                "quarkus.datasource.devservices.enabled", "false",
                // Bypass Flyway — migrations use H2-permissive SQL not valid on PostgreSQL.
                // Use Hibernate schema generation instead to get a valid schema for dialect testing.
                "quarkus.flyway.migrate-at-start", "false",
                "quarkus.hibernate-orm.database.generation", "drop-and-create");
    }

    @Override
    public void stop() {
        if (POSTGRES.isRunning()) {
            POSTGRES.stop();
        }
    }
}
