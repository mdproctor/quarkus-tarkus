package io.casehub.work.examples.businesshours;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;

@QuarkusTest
class BusinessHoursScenarioTest {

    @Test
    void run_loanApproval_setsBusinessHoursDeadline() {
        final Response response = given()
                .contentType(ContentType.JSON)
                .when().post("/examples/business-hours/run")
                .then().statusCode(200)
                .extract().response();

        assertThat(response.jsonPath().getString("scenario")).isEqualTo("business-hours-sla");
        assertThat(response.jsonPath().getString("workItemId")).isNotNull();
        assertThat(response.jsonPath().getString("expiresAt")).isNotNull();

        // Calendar hours must be ≥ 48 (business hours always ≥ wall-clock hours)
        final int calendarHours = response.jsonPath().getInt("calendarHoursToDeadline");
        assertThat(calendarHours).isGreaterThanOrEqualTo(48);

        // expiresAt must be in the future
        final Instant expiresAt = Instant.parse(response.jsonPath().getString("expiresAt"));
        assertThat(expiresAt).isAfter(Instant.now());

        // Steps logged
        final List<Map<String, Object>> steps = response.jsonPath().getList("steps");
        assertThat(steps).hasSize(2);
    }

    @Test
    void preview_returnsDeadlineForGivenHours() {
        final Response response = given()
                .queryParam("hours", "8")
                .queryParam("zone", "UTC")
                .when().get("/examples/business-hours/preview")
                .then().statusCode(200)
                .extract().response();

        assertThat(response.jsonPath().getInt("requestedBusinessHours")).isEqualTo(8);
        assertThat(response.jsonPath().getString("zone")).isEqualTo("UTC");
        assertThat(response.jsonPath().getString("deadline")).isNotNull();
        // Calendar hours spanned must be ≥ 8
        assertThat(response.jsonPath().getInt("calendarHoursSpanned")).isGreaterThanOrEqualTo(8);
    }

    @Test
    void preview_defaultsTo48Hours() {
        final Response response = given()
                .when().get("/examples/business-hours/preview")
                .then().statusCode(200)
                .extract().response();

        assertThat(response.jsonPath().getInt("requestedBusinessHours")).isEqualTo(48);
        assertThat(response.jsonPath().getInt("calendarHoursSpanned")).isGreaterThanOrEqualTo(48);
    }
}
