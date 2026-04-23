package io.quarkiverse.work.examples.queues.finance;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class FinanceApprovalScenarioTest {

    @Test
    void financeApproval_criticalSpend_getsExecReview() {
        given()
                .post("/queue-examples/finance/run")
                .then()
                .statusCode(200)
                .body("scenarioId", equalTo("finance-approval-chain"))
                // Step 1: NORMAL → standard approval only
                .body("steps[0].inferredLabels", hasItem("finance/approval"))
                .body("steps[0].inferredLabels", not(hasItem("finance/exec-review")))
                // Step 2: HIGH → standard approval only (CRITICAL threshold not met)
                .body("steps[1].inferredLabels", hasItem("finance/approval"))
                .body("steps[1].inferredLabels", not(hasItem("finance/exec-review")))
                // Step 3: CRITICAL → both queues
                .body("steps[2].inferredLabels", hasItems("finance/approval", "finance/exec-review"))
                // Queue contents
                .body("queueContents", hasSize(greaterThanOrEqualTo(1)));
    }

    @Test
    void financeApproval_queueEvents_normalExpense_addedToApprovalQueue() {
        given()
                .post("/queue-examples/finance/run")
                .then()
                .statusCode(200)
                .body("steps[0].queueEvents", hasItem(containsString("ADDED")))
                .body("steps[0].queueEvents", hasItem(containsString("Finance Approval Queue")));
    }

    @Test
    void financeApproval_queueEvents_criticalSpend_addedToBothQueues() {
        given()
                .post("/queue-examples/finance/run")
                .then()
                .statusCode(200)
                // CRITICAL item enters both Finance Approval Queue and Finance Exec Review Queue
                .body("steps[2].queueEvents", hasItem(containsString("Finance Approval Queue")))
                .body("steps[2].queueEvents", hasItem(containsString("Finance Exec Review Queue")))
                .body("steps[2].queueEvents", everyItem(containsString("ADDED")));
    }

    @Test
    void financeApproval_queueEvents_snapshotStep_hasNoEvents() {
        given()
                .post("/queue-examples/finance/run")
                .then()
                .statusCode(200)
                // The queue-snapshot step (step 4) doesn't create any WorkItem → no queue events
                .body("steps[3].queueEvents", empty());
    }
}
