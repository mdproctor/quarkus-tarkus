package io.quarkiverse.work.reports.api;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * Dialect validation: verifies HQL date_trunc('day') translates correctly against real PostgreSQL.
 *
 * <p>
 * Uses {@link PostgresTestResource} to start a PostgreSQL Testcontainer before Quarkus boots
 * and inject a real JDBC URL. This avoids the Quarkus Dev Services augmentation-time limitation
 * (Dev Services is disabled at augmentation if any JDBC URL is configured in application.properties).
 *
 * <p>
 * Requires Docker or a compatible socket (Podman, Colima). Skipped automatically if unavailable.
 */
@QuarkusTest
@QuarkusTestResource(value = PostgresTestResource.class, restrictToAnnotatedClass = true)
class PostgresDialectValidationTest {

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
