package io.quarkiverse.work.reports.api;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;

/**
 * Dialect validation: verifies HQL date_trunc('day') translates correctly against real PostgreSQL.
 *
 * <p>
 * Disabled by default — requires a separate Quarkus build with PostgreSQL as the datasource
 * (Quarkus augments the datasource driver at build time; switching at runtime via TestProfile
 * is not supported). To run these tests, build the module with a PostgreSQL-configured
 * application.properties and remove the {@code @Disabled} annotation.
 *
 * <p>
 * H2 2.x already validates the HQL {@code date_trunc} function at the syntax level in the
 * standard test suite; this class provides additional confirmation against a real PostgreSQL engine.
 */
@Disabled("Requires PostgreSQL-backed build; H2 suite covers date_trunc HQL syntax validation")
@QuarkusTest
@TestProfile(PostgresDialectValidationTest.PgProfile.class)
class PostgresDialectValidationTest {

    public static class PgProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("quarkus.datasource.db-kind", "postgresql");
        }

        @Override
        public String getConfigProfile() {
            return "postgres";
        }
    }

    @Test
    void throughput_groupByDay_executesOnPostgres() {
        given().contentType(ContentType.JSON)
                .body("{\"title\":\"PG Day Test\",\"createdBy\":\"pg-test\"}")
                .post("/workitems").then().statusCode(201);

        given().queryParam("from", Instant.now().minus(1, ChronoUnit.HOURS).toString())
                .queryParam("to", Instant.now().plus(1, ChronoUnit.HOURS).toString())
                .queryParam("groupBy", "day")
                .get("/workitems/reports/throughput")
                .then().statusCode(200)
                .body("buckets", notNullValue());
    }

    @Test
    void throughput_groupByWeek_executesOnPostgres() {
        given().contentType(ContentType.JSON)
                .body("{\"title\":\"PG Week Test\",\"createdBy\":\"pg-test\"}")
                .post("/workitems").then().statusCode(201);

        final var resp = given()
                .queryParam("from", Instant.now().minus(1, ChronoUnit.HOURS).toString())
                .queryParam("to", Instant.now().plus(1, ChronoUnit.HOURS).toString())
                .queryParam("groupBy", "week")
                .get("/workitems/reports/throughput")
                .then().statusCode(200).extract().response();

        final List<String> periods = resp.path("buckets.period");
        if (!periods.isEmpty()) {
            assertThat(periods.get(0)).matches("\\d{4}-W\\d{2}");
        }
    }

    @Test
    void throughput_groupByMonth_executesOnPostgres() {
        given().contentType(ContentType.JSON)
                .body("{\"title\":\"PG Month Test\",\"createdBy\":\"pg-test\"}")
                .post("/workitems").then().statusCode(201);

        final var resp = given()
                .queryParam("from", Instant.now().minus(1, ChronoUnit.HOURS).toString())
                .queryParam("to", Instant.now().plus(1, ChronoUnit.HOURS).toString())
                .queryParam("groupBy", "month")
                .get("/workitems/reports/throughput")
                .then().statusCode(200).extract().response();

        final List<String> periods = resp.path("buckets.period");
        if (!periods.isEmpty()) {
            assertThat(periods.get(0)).matches("\\d{4}-\\d{2}");
        }
    }

    @Test
    void slaBreaches_executesOnPostgres() {
        given().get("/workitems/reports/sla-breaches")
                .then().statusCode(200)
                .body("items", notNullValue());
    }

    @Test
    void queueHealth_executesOnPostgres() {
        given().get("/workitems/reports/queue-health")
                .then().statusCode(200)
                .body("overdueCount", notNullValue());
    }
}
