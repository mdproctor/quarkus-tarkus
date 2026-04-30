package io.casehub.work.runtime.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

/**
 * Integration tests for Micrometer metrics endpoint.
 */
@QuarkusTest
class MetricsEndpointTest {

    @Test
    void metricsEndpoint_returns200() {
        given().get("/q/metrics").then().statusCode(200);
    }

    @Test
    void metrics_containsWorkItemsActiveGauge() {
        given().get("/q/metrics")
                .then().statusCode(200)
                .body(containsString("workitems_active"));
    }

    @Test
    void metrics_containsWorkItemsByStatusGauge() {
        given().get("/q/metrics")
                .then().statusCode(200)
                .body(containsString("workitems_by_status"));
    }

    @Test
    void metrics_containsOverdueGauge() {
        given().get("/q/metrics")
                .then().statusCode(200)
                .body(containsString("workitems_overdue"));
    }

    @Test
    void metrics_containsLifecycleEventCounter() {
        // Trigger a lifecycle event so the counter exists
        given().contentType(io.restassured.http.ContentType.JSON)
                .body("{\"title\":\"Metrics test\",\"createdBy\":\"test\"}")
                .post("/workitems").then().statusCode(201);

        given().get("/q/metrics")
                .then().statusCode(200)
                .body(containsString("workitems_lifecycle_events"));
    }
}
