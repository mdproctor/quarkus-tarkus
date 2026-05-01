package io.casehub.work.runtime.calendar;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * Integration tests for business hours deadline resolution via the REST API.
 *
 * <p>
 * The test application.properties configures Mon-Fri 09:00-17:00 UTC.
 * These tests verify that business hours fields are resolved correctly
 * at WorkItem creation time.
 */
@QuarkusTest
class BusinessHoursIntegrationTest {

    @Test
    void createWithExpiresAtBusinessHours_setsAbsoluteExpiresAt() {
        // Capture now before the REST call — business hours rounds up to next minute boundary,
        // so the assertion upper bound must be captured before the service runs.
        final Instant before = Instant.now();

        final var body = Map.of(
                "title", "BH expiry test",
                "category", "test",
                "createdBy", "test",
                "expiresAtBusinessHours", 8);

        final String id = given()
                .contentType(ContentType.JSON)
                .body(body)
                .when().post("/workitems")
                .then().statusCode(201)
                .extract().path("id");

        final String expiresAtStr = given()
                .when().get("/workitems/" + id)
                .then().statusCode(200)
                .extract().path("expiresAt");

        assertThat(expiresAtStr).isNotNull();
        // expiresAt must be in the future and not more than 8 business hours away
        final Instant expiresAt = Instant.parse(expiresAtStr);
        assertThat(expiresAt).isAfter(before);
        // 8 business hours ≤ 3 calendar days; +5 min buffer for minute-boundary rounding
        assertThat(expiresAt).isBefore(before.plus(3, ChronoUnit.DAYS).plus(5, ChronoUnit.MINUTES));
    }

    @Test
    void createWithClaimDeadlineBusinessHours_setsAbsoluteClaimDeadline() {
        final var body = Map.of(
                "title", "BH claim test",
                "category", "test",
                "createdBy", "test",
                "claimDeadlineBusinessHours", 2);

        final String id = given()
                .contentType(ContentType.JSON)
                .body(body)
                .when().post("/workitems")
                .then().statusCode(201)
                .extract().path("id");

        final String claimDeadlineStr = given()
                .when().get("/workitems/" + id)
                .then().statusCode(200)
                .extract().path("claimDeadline");

        assertThat(claimDeadlineStr).isNotNull();
        final Instant claimDeadline = Instant.parse(claimDeadlineStr);
        assertThat(claimDeadline).isAfter(Instant.now());
        // 2 business hours ≤ 1 calendar day
        assertThat(claimDeadline).isBefore(Instant.now().plus(1, ChronoUnit.DAYS));
    }

    @Test
    void absoluteExpiresAt_takesPrecedenceOverBusinessHours() {
        final Instant absolute = Instant.now().plus(72, ChronoUnit.HOURS);
        final var body = Map.of(
                "title", "Precedence test",
                "category", "test",
                "createdBy", "test",
                "expiresAt", absolute.toString(),
                "expiresAtBusinessHours", 1);

        final String id = given()
                .contentType(ContentType.JSON)
                .body(body)
                .when().post("/workitems")
                .then().statusCode(201)
                .extract().path("id");

        final String expiresAtStr = given()
                .when().get("/workitems/" + id)
                .then().statusCode(200)
                .extract().path("expiresAt");

        // absolute takes precedence — should be ≈ 72h away (within a few seconds)
        final Instant result = Instant.parse(expiresAtStr);
        assertThat(result).isCloseTo(absolute, org.assertj.core.api.Assertions.within(5, ChronoUnit.SECONDS));
    }

    @Test
    void templateWithDefaultExpiryBusinessHours_instantiate_setsAbsoluteExpiresAt() {
        // Capture now before the REST calls — same minute-rounding consideration as above.
        final Instant before = Instant.now();

        // Create template with 8 business hours expiry
        final String tmplId = given()
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "name", "BH template test",
                        "category", "bh-test",
                        "createdBy", "test",
                        "defaultExpiryBusinessHours", 8))
                .when().post("/workitem-templates")
                .then().statusCode(201)
                .extract().path("id");

        // Instantiate the template
        final String wiId = given()
                .contentType(ContentType.JSON)
                .body(Map.of("createdBy", "test"))
                .when().post("/workitem-templates/" + tmplId + "/instantiate")
                .then().statusCode(201)
                .extract().path("id");

        final String expiresAtStr = given()
                .when().get("/workitems/" + wiId)
                .then().statusCode(200)
                .extract().path("expiresAt");

        assertThat(expiresAtStr).isNotNull();
        final Instant expiresAt = Instant.parse(expiresAtStr);
        // 8 business hours → within 3 calendar days from before; +5 min buffer for minute-boundary rounding
        assertThat(expiresAt).isAfter(before);
        assertThat(expiresAt).isBefore(before.plus(3, ChronoUnit.DAYS).plus(5, ChronoUnit.MINUTES));
    }

    @Test
    void businessHours_lessThanEquivalentWallClock() {
        // 8 business hours must result in an expiresAt later than 8 wall-clock hours
        // (because business hours skip nights/weekends — the deadline is always further in calendar time)
        final Instant wallClock8h = Instant.now().plus(8, ChronoUnit.HOURS);

        final String id = given()
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "title", "BH vs wall clock",
                        "category", "test",
                        "createdBy", "test",
                        "expiresAtBusinessHours", 8))
                .when().post("/workitems")
                .then().statusCode(201)
                .extract().path("id");

        final Instant bhExpiresAt = Instant.parse(given()
                .when().get("/workitems/" + id)
                .then().statusCode(200)
                .extract().path("expiresAt").toString());

        // 8 biz hours from now must be at least 8 wall-clock hours in the future
        // (could be much later if the window is currently closed or weekend is crossed)
        assertThat(bhExpiresAt).isAfterOrEqualTo(wallClock8h.minus(1, ChronoUnit.MINUTES));
    }
}
