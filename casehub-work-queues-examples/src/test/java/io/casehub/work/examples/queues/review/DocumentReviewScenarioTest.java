package io.casehub.work.examples.queues.review;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class DocumentReviewScenarioTest {

    @Test
    void documentReview_lambdaOverridesNormalPriorityToUrgent() {
        given()
                .post("/queue-examples/review/run?delay=0")
                .then()
                .statusCode(200)
                .body("scenarioId", equalTo("document-review-pipeline"))
                // Step 1: Lambda overrides NORMAL→urgent
                .body("steps[0].inferredLabels", hasItems("review/urgent", "review/urgent/unassigned"))
                .body("steps[0].inferredLabels", not(hasItem("review/routine")))
                // Step 2: HIGH → standard
                .body("steps[1].inferredLabels", hasItems("review/standard", "review/standard/unassigned"))
                .body("steps[1].inferredLabels", not(hasItem("review/urgent")))
                // Step 3: NORMAL → routine
                .body("steps[2].inferredLabels", hasItems("review/routine", "review/routine/unassigned"))
                .body("steps[2].inferredLabels", not(hasItem("review/urgent")))
                // Step 4: after claim — unassigned→claimed
                .body("steps[3].inferredLabels", hasItem("review/urgent"))
                .body("steps[3].inferredLabels", hasItem("review/urgent/claimed"))
                .body("steps[3].inferredLabels", not(hasItem("review/urgent/unassigned")))
                // Step 5: after start — claimed→active
                .body("steps[4].inferredLabels", hasItem("review/urgent"))
                .body("steps[4].inferredLabels", hasItem("review/urgent/active"))
                .body("steps[4].inferredLabels", not(hasItem("review/urgent/claimed")))
                // Step 6: queue snapshot — urgent/unassigned empty (advisory in active);
                // others >= 1 (exact count grows with each test run due to shared H2 DB)
                .body("steps[5].inferredLabels", hasItem(containsString("review/urgent/unassigned: 0")))
                .body("steps[5].inferredLabels", hasItem(matchesPattern("review/urgent/active: [1-9].*")))
                .body("steps[5].inferredLabels", hasItem(matchesPattern("review/standard/unassigned: [1-9].*")))
                .body("steps[5].inferredLabels", hasItem(matchesPattern("review/routine/unassigned: [1-9].*")))
                // Step 7: after complete — all inferred labels gone
                .body("steps[6].inferredLabels", empty());
    }

    @Test
    void documentReview_queueEvents_securityAdvisory_addedToUrgentQueue() {
        given()
                .post("/queue-examples/review/run?delay=0")
                .then()
                .statusCode(200)
                // Step 1: Lambda overrides NORMAL → added to Urgent Reviews
                .body("steps[0].queueEvents", hasItem(containsString("ADDED")))
                .body("steps[0].queueEvents", hasItem(containsString("Urgent Reviews")));
    }

    @Test
    void documentReview_queueEvents_releaseNotes_addedToStandardQueue() {
        given()
                .post("/queue-examples/review/run?delay=0")
                .then()
                .statusCode(200)
                .body("steps[1].queueEvents", hasItem(containsString("ADDED")))
                .body("steps[1].queueEvents", hasItem(containsString("Standard Reviews")));
    }

    @Test
    void documentReview_queueEvents_tutorial_addedToRoutineQueue() {
        given()
                .post("/queue-examples/review/run?delay=0")
                .then()
                .statusCode(200)
                .body("steps[2].queueEvents", hasItem(containsString("ADDED")))
                .body("steps[2].queueEvents", hasItem(containsString("Routine Reviews")));
    }

    @Test
    void documentReview_queueEvents_claimAndStart_fireChanged() {
        given()
                .post("/queue-examples/review/run?delay=0")
                .then()
                .statusCode(200)
                // Claim (step 4): item stays in Urgent Reviews → CHANGED
                .body("steps[3].queueEvents", hasItem(containsString("CHANGED")))
                .body("steps[3].queueEvents", hasItem(containsString("Urgent Reviews")))
                // Start (step 5): item stays in Urgent Reviews → CHANGED
                .body("steps[4].queueEvents", hasItem(containsString("CHANGED")))
                .body("steps[4].queueEvents", hasItem(containsString("Urgent Reviews")));
    }

    @Test
    void documentReview_queueEvents_complete_removedFromUrgentQueue() {
        given()
                .post("/queue-examples/review/run?delay=0")
                .then()
                .statusCode(200)
                // Complete (step 7): COMPLETED state — notTerminal guard fails, all labels removed → REMOVED
                .body("steps[6].queueEvents", hasItem(containsString("REMOVED")))
                .body("steps[6].queueEvents", hasItem(containsString("Urgent Reviews")));
    }

    @Test
    void documentReview_queueEvents_snapshotStep_hasNoEvents() {
        given()
                .post("/queue-examples/review/run?delay=0")
                .then()
                .statusCode(200)
                .body("steps[5].queueEvents", empty());
    }
}
