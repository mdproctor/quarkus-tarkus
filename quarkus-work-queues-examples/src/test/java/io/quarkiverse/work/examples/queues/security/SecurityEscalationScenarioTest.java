package io.quarkiverse.work.examples.queues.security;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class SecurityEscalationScenarioTest {

    @Test
    void security_criticalBreach_triggersExecEscalateCascade() {
        given()
                .post("/queue-examples/security/run")
                .then()
                .statusCode(200)
                .body("scenarioId", equalTo("security-exec-escalation"))
                .body("steps[0].inferredLabels", hasItem("security/incident"))
                .body("steps[0].inferredLabels", not(hasItem("priority/critical")))
                .body("steps[0].inferredLabels", not(hasItem("security/exec-escalate")))
                .body("steps[1].inferredLabels", hasItems(
                        "security/incident", "priority/critical", "security/exec-escalate"))
                .body("queueContents", hasSize(greaterThanOrEqualTo(1)));
    }

    @Test
    void security_queueEvents_highIncident_addedToIncidentQueueOnly() {
        given()
                .post("/queue-examples/security/run")
                .then()
                .statusCode(200)
                .body("steps[0].queueEvents", hasItem(containsString("ADDED")))
                .body("steps[0].queueEvents", hasItem(containsString("Security Incidents Queue")))
                // HIGH incident does NOT trigger critical or exec escalation queues
                .body("steps[0].queueEvents", not(hasItem(containsString("Priority Critical Queue"))))
                .body("steps[0].queueEvents", not(hasItem(containsString("Security Exec Escalation Queue"))));
    }

    @Test
    void security_queueEvents_criticalBreach_addedToAllThreeQueues() {
        given()
                .post("/queue-examples/security/run")
                .then()
                .statusCode(200)
                // CRITICAL breach enters all 3 queues via cascade
                .body("steps[1].queueEvents", hasItem(containsString("Security Incidents Queue")))
                .body("steps[1].queueEvents", hasItem(containsString("Priority Critical Queue")))
                .body("steps[1].queueEvents", hasItem(containsString("Security Exec Escalation Queue")))
                .body("steps[1].queueEvents", everyItem(containsString("ADDED")));
    }

    @Test
    void security_queueEvents_snapshotStep_hasNoEvents() {
        given()
                .post("/queue-examples/security/run")
                .then()
                .statusCode(200)
                .body("steps[2].queueEvents", empty());
    }
}
