package io.casehub.work.postgres.broadcaster;

import java.util.Map;

import org.testcontainers.containers.PostgreSQLContainer;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

/**
 * Starts a real PostgreSQL container before Quarkus boots and injects JDBC + reactive URLs.
 *
 * <p>
 * Both JDBC (for JPA/Flyway via Agroal) and reactive (for LISTEN/NOTIFY via the pg client)
 * datasource URLs are configured. The reactive URL uses the {@code postgresql://} scheme
 * (no {@code jdbc:} prefix) as required by the Vert.x reactive client.
 */
public class PostgresBroadcasterTestResource implements QuarkusTestResourceLifecycleManager {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Override
    public Map<String, String> start() {
        POSTGRES.start();
        // Derive reactive URL from the container's JDBC URL by stripping the jdbc: prefix
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
        if (POSTGRES.isRunning()) {
            POSTGRES.stop();
        }
    }
}
