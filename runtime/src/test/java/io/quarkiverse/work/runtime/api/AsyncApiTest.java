package io.casehub.work.runtime.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

/**
 * Verifies the AsyncAPI spec endpoint and content.
 */
@QuarkusTest
class AsyncApiTest {

    @Test
    void asyncApiEndpoint_returns200() {
        given().get("/q/asyncapi").then().statusCode(200);
    }

    @Test
    void asyncApiSpec_containsLifecycleEventChannel() {
        given().get("/q/asyncapi")
                .then().statusCode(200)
                .body(containsString("WorkItemLifecycleEvent"));
    }

    @Test
    void asyncApiSpec_containsQueueEventChannel() {
        given().get("/q/asyncapi")
                .then().statusCode(200)
                .body(containsString("WorkItemQueueEvent"));
    }

    @Test
    void asyncApiSpec_containsWorkItemStatus() {
        given().get("/q/asyncapi")
                .then().statusCode(200)
                .body(containsString("WorkItemStatus"));
    }

    @Test
    void asyncApiSpec_containsQueueEventType() {
        given().get("/q/asyncapi")
                .then().statusCode(200)
                .body(containsString("QueueEventType"));
    }

    @Test
    void asyncApiSpec_documentsFiringContract() {
        // The critical tracker-not-live-snapshot invariant must be documented
        given().get("/q/asyncapi")
                .then().statusCode(200)
                .body(containsString("post-mutation"));
    }
}
