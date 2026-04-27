package io.quarkiverse.work.reports.api;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

@QuarkusTest
class ThroughputReportTest {

    @Test
    void missingFrom_returns400() {
        given().queryParam("to", "2026-04-27T00:00:00Z")
                .get("/workitems/reports/throughput")
                .then().statusCode(400);
    }

    @Test
    void missingTo_returns400() {
        given().queryParam("from", "2026-04-01T00:00:00Z")
                .get("/workitems/reports/throughput")
                .then().statusCode(400);
    }

    @Test
    void missingBoth_returns400() {
        given().get("/workitems/reports/throughput").then().statusCode(400);
    }

    @Test
    void invalidGroupBy_returns400() {
        given().queryParam("from", "2026-04-01T00:00:00Z")
                .queryParam("to", "2026-04-30T00:00:00Z")
                .queryParam("groupBy", "quarter")
                .get("/workitems/reports/throughput")
                .then().statusCode(400);
    }

    @Test
    void invalidTimestamp_returns400() {
        given().queryParam("from", "not-a-date")
                .queryParam("to", "2026-04-30T00:00:00Z")
                .get("/workitems/reports/throughput")
                .then().statusCode(400);
    }

    @Test
    void report_returns200_withExpectedStructure() {
        final String from = Instant.now().minus(1, ChronoUnit.HOURS).toString();
        final String to = Instant.now().plus(1, ChronoUnit.HOURS).toString();

        given().queryParam("from", from).queryParam("to", to)
                .get("/workitems/reports/throughput")
                .then().statusCode(200)
                .body("from", notNullValue())
                .body("to", notNullValue())
                .body("groupBy", equalTo("day"))
                .body("buckets", notNullValue());
    }

    @Test
    void groupByDay_defaultWhenOmitted() {
        given().queryParam("from", Instant.now().minus(1, ChronoUnit.HOURS).toString())
                .queryParam("to", Instant.now().plus(1, ChronoUnit.HOURS).toString())
                .get("/workitems/reports/throughput")
                .then().statusCode(200)
                .body("groupBy", equalTo("day"));
    }

    @Test
    void createdItem_appearsInBucket() {
        final String from = Instant.now().minus(1, ChronoUnit.HOURS).toString();
        final String to = Instant.now().plus(1, ChronoUnit.HOURS).toString();

        given().contentType(ContentType.JSON)
                .body("{\"title\":\"Throughput Test\",\"createdBy\":\"test\"}")
                .post("/workitems").then().statusCode(201);

        final var resp = given().queryParam("from", from).queryParam("to", to)
                .queryParam("groupBy", "day")
                .get("/workitems/reports/throughput")
                .then().statusCode(200).extract().response();

        final List<Integer> created = resp.path("buckets.created");
        assertThat(created.stream().mapToInt(Integer::intValue).sum()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void itemsOutsideRange_excluded() {
        given().queryParam("from", "2000-01-01T00:00:00Z")
                .queryParam("to", "2000-12-31T23:59:59Z")
                .get("/workitems/reports/throughput")
                .then().statusCode(200)
                .body("buckets", empty());
    }

    @Test
    void groupByWeek_returnsBucketWithWeekLabel() {
        given().contentType(ContentType.JSON)
                .body("{\"title\":\"Week Test\",\"createdBy\":\"test\"}")
                .post("/workitems").then().statusCode(201);

        final var resp = given()
                .queryParam("from", Instant.now().minus(1, ChronoUnit.HOURS).toString())
                .queryParam("to", Instant.now().plus(1, ChronoUnit.HOURS).toString())
                .queryParam("groupBy", "week")
                .get("/workitems/reports/throughput")
                .then().statusCode(200).extract().response();

        final List<String> periods = resp.path("buckets.period");
        assertThat(periods).isNotEmpty();
        assertThat(periods.get(0)).matches("\\d{4}-W\\d{2}");
    }

    @Test
    void groupByMonth_returnsBucketWithMonthLabel() {
        given().contentType(ContentType.JSON)
                .body("{\"title\":\"Month Test\",\"createdBy\":\"test\"}")
                .post("/workitems").then().statusCode(201);

        final var resp = given()
                .queryParam("from", Instant.now().minus(1, ChronoUnit.HOURS).toString())
                .queryParam("to", Instant.now().plus(1, ChronoUnit.HOURS).toString())
                .queryParam("groupBy", "month")
                .get("/workitems/reports/throughput")
                .then().statusCode(200).extract().response();

        final List<String> periods = resp.path("buckets.period");
        assertThat(periods).isNotEmpty();
        assertThat(periods.get(0)).matches("\\d{4}-\\d{2}");
    }

    @Test
    void inFlightItem_createdCountHigherThanCompleted() {
        final String from = Instant.now().minus(1, ChronoUnit.HOURS).toString();
        final String to = Instant.now().plus(1, ChronoUnit.HOURS).toString();

        // create but don't complete
        given().contentType(ContentType.JSON)
                .body("{\"title\":\"In Flight\",\"createdBy\":\"test\"}")
                .post("/workitems").then().statusCode(201);

        final var resp = given().queryParam("from", from).queryParam("to", to)
                .queryParam("groupBy", "day")
                .get("/workitems/reports/throughput")
                .then().statusCode(200).extract().response();

        final List<Integer> created = resp.path("buckets.created");
        final List<Integer> completed = resp.path("buckets.completed");
        final int totalCreated = created.stream().mapToInt(Integer::intValue).sum();
        final int totalCompleted = completed.stream().mapToInt(Integer::intValue).sum();
        assertThat(totalCreated).isGreaterThanOrEqualTo(totalCompleted);
    }

    @Test
    void emptyRange_returnsEmptyBuckets() {
        given().queryParam("from", "2099-01-01T00:00:00Z")
                .queryParam("to", "2099-12-31T23:59:59Z")
                .get("/workitems/reports/throughput")
                .then().statusCode(200)
                .body("buckets", empty());
    }

    @Test
    void e2e_createdAndCompleted_appearsInBuckets() {
        final String from = Instant.now().minus(1, ChronoUnit.HOURS).toString();
        final String to = Instant.now().plus(1, ChronoUnit.HOURS).toString();

        final String id = given().contentType(ContentType.JSON)
                .body("{\"title\":\"E2E Throughput\",\"createdBy\":\"test\"}")
                .post("/workitems").then().statusCode(201).extract().path("id");
        given().put("/workitems/" + id + "/claim?claimant=actor").then().statusCode(200);
        given().put("/workitems/" + id + "/start?actor=actor").then().statusCode(200);
        given().contentType(ContentType.JSON).body("{}")
                .put("/workitems/" + id + "/complete?actor=actor").then().statusCode(200);

        final var resp = given().queryParam("from", from).queryParam("to", to)
                .queryParam("groupBy", "day")
                .get("/workitems/reports/throughput")
                .then().statusCode(200).extract().response();

        final List<Integer> created = resp.path("buckets.created");
        final List<Integer> completed = resp.path("buckets.completed");
        assertThat(created.stream().mapToInt(Integer::intValue).sum()).isGreaterThanOrEqualTo(1);
        assertThat(completed.stream().mapToInt(Integer::intValue).sum()).isGreaterThanOrEqualTo(1);
    }
}
