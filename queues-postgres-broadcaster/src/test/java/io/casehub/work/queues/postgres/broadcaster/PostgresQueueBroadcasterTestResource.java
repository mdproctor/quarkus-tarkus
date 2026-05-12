package io.casehub.work.queues.postgres.broadcaster;

import java.util.Map;

import org.testcontainers.containers.PostgreSQLContainer;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

/** Starts a real PostgreSQL container and injects JDBC + reactive datasource URLs. */
public class PostgresQueueBroadcasterTestResource implements QuarkusTestResourceLifecycleManager {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Override
    public Map<String, String> start() {
        POSTGRES.start();
        final String reactiveUrl = POSTGRES.getJdbcUrl().replace("jdbc:", "");
        return Map.of(
                "quarkus.datasource.db-kind", "postgresql",
                "quarkus.datasource.jdbc.url", POSTGRES.getJdbcUrl(),
                "quarkus.datasource.reactive.url", reactiveUrl,
                "quarkus.datasource.username", POSTGRES.getUsername(),
                "quarkus.datasource.password", POSTGRES.getPassword(),
                "quarkus.datasource.devservices.enabled", "false",
                "quarkus.flyway.migrate-at-start", "true");
    }

    @Override
    public void stop() {
        if (POSTGRES.isRunning()) POSTGRES.stop();
    }
}
