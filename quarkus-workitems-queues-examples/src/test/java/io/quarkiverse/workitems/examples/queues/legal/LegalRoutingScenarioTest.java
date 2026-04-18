package io.quarkiverse.workitems.examples.queues.legal;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class LegalRoutingScenarioTest {

    @Test
    void legalRouting_normalItem_getsReviewOnly() {
        given()
                .post("/queue-examples/legal/run")
                .then()
                .statusCode(200)
                .body("scenarioId", equalTo("legal-compliance-routing"))
                .body("steps[0].inferredLabels", hasItem("legal/review"))
                .body("steps[0].inferredLabels", not(hasItem("legal/urgent")))
                .body("steps[1].inferredLabels", hasItems("legal/review", "legal/urgent"))
                .body("queueContents", hasSize(greaterThanOrEqualTo(1)));
    }

    @Test
    void legalRouting_queueEvents_normalItem_addedToReviewQueueOnly() {
        given()
                .post("/queue-examples/legal/run")
                .then()
                .statusCode(200)
                .body("steps[0].queueEvents", hasItem(containsString("ADDED")))
                .body("steps[0].queueEvents", hasItem(containsString("Legal Review Queue")))
                .body("steps[0].queueEvents", not(hasItem(containsString("Legal Urgent Queue"))));
    }

    @Test
    void legalRouting_queueEvents_highItem_addedToBothQueues() {
        given()
                .post("/queue-examples/legal/run")
                .then()
                .statusCode(200)
                .body("steps[1].queueEvents", hasItem(containsString("Legal Review Queue")))
                .body("steps[1].queueEvents", hasItem(containsString("Legal Urgent Queue")))
                .body("steps[1].queueEvents", everyItem(containsString("ADDED")));
    }

    @Test
    void legalRouting_queueEvents_snapshotStep_hasNoEvents() {
        given()
                .post("/queue-examples/legal/run")
                .then()
                .statusCode(200)
                .body("steps[2].queueEvents", empty());
    }
}
