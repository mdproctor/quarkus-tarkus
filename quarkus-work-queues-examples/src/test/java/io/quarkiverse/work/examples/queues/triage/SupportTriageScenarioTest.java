package io.quarkiverse.work.examples.queues.triage;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class SupportTriageScenarioTest {

    @Test
    void supportTriage_criticalTicket_getsFastTrackLabel() {
        given()
                .post("/queue-examples/triage/run")
                .then()
                .statusCode(200)
                .body("scenarioId", equalTo("support-triage-cascade"))
                .body("steps[0].inferredLabels", hasItems("sla/critical", "queue/fast-track"))
                .body("steps[0].inferredLabels", not(hasItem("intake/triage")))
                .body("steps[1].inferredLabels", hasItems("intake/triage", "team/support-lead"))
                .body("steps[2].inferredLabels", not(hasItem("intake/triage")))
                .body("steps[2].inferredLabels", not(hasItem("team/support-lead")))
                .body("queueContents", hasSize(greaterThanOrEqualTo(1)));
    }

    @Test
    void supportTriage_queueEvents_criticalTicket_addedToTwoQueues() {
        given()
                .post("/queue-examples/triage/run")
                .then()
                .statusCode(200)
                // CRITICAL ticket enters SLA Critical Queue and Fast Track Queue
                .body("steps[0].queueEvents", hasItem(containsString("SLA Critical Queue")))
                .body("steps[0].queueEvents", hasItem(containsString("Fast Track Queue")))
                .body("steps[0].queueEvents", everyItem(containsString("ADDED")));
    }

    @Test
    void supportTriage_queueEvents_highTicket_addedToTriageAndLeadQueues() {
        given()
                .post("/queue-examples/triage/run")
                .then()
                .statusCode(200)
                // HIGH unassigned ticket enters Intake Triage Queue and Support Lead Queue (cascade)
                .body("steps[1].queueEvents", hasItem(containsString("Intake Triage Queue")))
                .body("steps[1].queueEvents", hasItem(containsString("Support Lead Queue")))
                .body("steps[1].queueEvents", everyItem(containsString("ADDED")));
    }

    @Test
    void supportTriage_queueEvents_afterClaim_removedFromTriageQueues() {
        given()
                .post("/queue-examples/triage/run")
                .then()
                .statusCode(200)
                // After claim: HIGH ticket leaves Intake Triage Queue and Support Lead Queue
                .body("steps[2].queueEvents", hasItem(containsString("REMOVED")))
                .body("steps[2].queueEvents", hasItem(containsString("Intake Triage Queue")))
                .body("steps[2].queueEvents", hasItem(containsString("Support Lead Queue")));
    }

    @Test
    void supportTriage_queueEvents_snapshotStep_hasNoEvents() {
        given()
                .post("/queue-examples/triage/run")
                .then()
                .statusCode(200)
                .body("steps[3].queueEvents", empty());
    }
}
