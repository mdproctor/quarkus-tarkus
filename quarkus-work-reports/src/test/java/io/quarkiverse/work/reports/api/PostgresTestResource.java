package io.quarkiverse.work.reports.api;

import java.util.Map;

import org.testcontainers.containers.PostgreSQLContainer;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

/**
 * Starts a real PostgreSQL container before Quarkus boots and injects a concrete JDBC URL.
 * Flyway runs normally — migrations now use standard SQL types (DOUBLE PRECISION, not DOUBLE).
 *
 * <p>
 * Must be paired with the postgres-dialect-test Surefire execution, which sets
 * quarkus.datasource.db-kind=postgresql as a system property BEFORE Quarkus augmentation
 * so Agroal is configured with the PostgreSQL driver class.
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
                "quarkus.datasource.devservices.enabled", "false");
    }

    @Override
    public void stop() {
        if (POSTGRES.isRunning()) {
            POSTGRES.stop();
        }
    }
}
