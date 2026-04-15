package io.quarkiverse.workitems.examples.expense;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;

@QuarkusTest
class ExpenseApprovalScenarioTest {

    @Test
    void run_expenseApproval_producesFullLifecycleWithHashChain() {
        final Response response = given()
                .contentType(ContentType.JSON)
                .when()
                .post("/examples/expense/run")
                .then()
                .statusCode(200)
                .extract().response();

        // Scenario identity
        assertThat(response.jsonPath().getString("scenario")).isEqualTo("expense-approval");

        // Steps logged
        final List<Map<String, Object>> steps = response.jsonPath().getList("steps");
        assertThat(steps).hasSize(4);
        assertThat(steps.get(0).get("description").toString()).contains("Creates expense WorkItem");
        assertThat(steps.get(1).get("description").toString()).containsIgnoringCase("claims");
        assertThat(steps.get(2).get("description").toString()).containsIgnoringCase("starts");
        assertThat(steps.get(3).get("description").toString()).containsIgnoringCase("completes");

        // WorkItem ID present
        assertThat(response.jsonPath().getString("workItemId")).isNotNull();

        // Ledger: exactly 4 entries (create, claim, start, complete)
        final List<Map<String, Object>> ledger = response.jsonPath().getList("ledgerEntries");
        assertThat(ledger).hasSize(4);

        // Entry 1: WorkItemCreated by finance-system
        final Map<String, Object> entry1 = ledger.get(0);
        assertThat(entry1.get("commandType")).isEqualTo("CreateWorkItem");
        assertThat(entry1.get("eventType")).isEqualTo("WorkItemCreated");
        assertThat(entry1.get("actorId")).isEqualTo("finance-system");
        assertThat(entry1.get("digest")).isNotNull();
        assertThat(entry1.get("previousHash")).isNull();
        assertThat(entry1.get("decisionContext")).isNotNull();
        assertThat(entry1.get("decisionContext").toString()).containsIgnoringCase("PENDING");

        // Entry 4: WorkItemCompleted by alice
        final Map<String, Object> entry4 = ledger.get(3);
        assertThat(entry4.get("commandType")).isEqualTo("CompleteWorkItem");
        assertThat(entry4.get("eventType")).isEqualTo("WorkItemCompleted");
        assertThat(entry4.get("actorId")).isEqualTo("alice");
        assertThat(entry4.get("decisionContext").toString()).containsIgnoringCase("COMPLETED");

        // Hash chain integrity: each entry's previousHash == prior entry's digest
        for (int i = 1; i < ledger.size(); i++) {
            final String prevDigest = ledger.get(i - 1).get("digest").toString();
            final String currentPreviousHash = ledger.get(i).get("previousHash").toString();
            assertThat(currentPreviousHash)
                    .as("Entry %d previousHash should equal entry %d digest", i + 1, i)
                    .isEqualTo(prevDigest);
        }

        // Audit trail present
        final List<Map<String, Object>> audit = response.jsonPath().getList("auditTrail");
        assertThat(audit).hasSizeGreaterThanOrEqualTo(4);
    }
}
