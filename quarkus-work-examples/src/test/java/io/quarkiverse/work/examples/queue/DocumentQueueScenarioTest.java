package io.quarkiverse.work.examples.queue;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;

@QuarkusTest
class DocumentQueueScenarioTest {

    @Test
    @SuppressWarnings("unchecked")
    void run_documentQueue_demonstratesWorkQueuesAndTrustScores() {
        final Response response = given()
                .contentType(ContentType.JSON)
                .when()
                .post("/examples/queue/run")
                .then()
                .statusCode(200)
                .extract().response();

        assertThat(response.jsonPath().getString("scenario")).isEqualTo("document-queue");

        // 3 WorkItems created
        final List<String> workItemIds = response.jsonPath().getList("workItemIds");
        assertThat(workItemIds).hasSize(3);

        // Total ledger entries: 14 across all 3 WorkItems
        final List<Map<String, Object>> allLedger = response.jsonPath().getList("allLedgerEntries");
        assertThat(allLedger).hasSize(14);

        // Release entry exists for reviewer-alice
        final boolean hasRelease = allLedger.stream()
                .anyMatch(e -> "WorkItemReleased".equals(e.get("eventType"))
                        && "reviewer-alice".equals(e.get("actorId")));
        assertThat(hasRelease).as("Expected a release entry from reviewer-alice").isTrue();

        // reviewer-bob has exactly 2 completion entries (WI-1 after alice released, and WI-2)
        final long bobCompletions = allLedger.stream()
                .filter(e -> "WorkItemCompleted".equals(e.get("eventType"))
                        && "reviewer-bob".equals(e.get("actorId")))
                .count();
        assertThat(bobCompletions).isEqualTo(2);

        // reviewer-alice has exactly 1 completion entry
        final long aliceCompletions = allLedger.stream()
                .filter(e -> "WorkItemCompleted".equals(e.get("eventType"))
                        && "reviewer-alice".equals(e.get("actorId")))
                .count();
        assertThat(aliceCompletions).isEqualTo(1);

        // Trust scores present and in valid range
        final Map<String, Object> bobTrust = response.jsonPath().getMap("reviewerBobTrust");
        assertThat(bobTrust).isNotNull();
        assertThat(bobTrust.get("actorId")).isEqualTo("reviewer-bob");
        final double bobTrustScore = ((Number) bobTrust.get("trustScore")).doubleValue();
        assertThat(bobTrustScore).isBetween(0.0, 1.0);
        assertThat(((Number) bobTrust.get("decisionCount")).intValue()).isGreaterThan(0);

        final Map<String, Object> aliceTrust = response.jsonPath().getMap("reviewerAliceTrust");
        assertThat(aliceTrust).isNotNull();
        assertThat(aliceTrust.get("actorId")).isEqualTo("reviewer-alice");
        final double aliceTrustScore = ((Number) aliceTrust.get("trustScore")).doubleValue();
        assertThat(aliceTrustScore).isBetween(0.0, 1.0);

        // Bob scores >= alice (bob: 2 completions, no releases; alice: 1 completion, 1 release)
        final double bobScore = bobTrustScore;
        final double aliceScore = aliceTrustScore;
        assertThat(bobScore).as("Bob should score >= alice (more completions, no releases)")
                .isGreaterThanOrEqualTo(aliceScore);

        // All entries have digest (hash chain active)
        allLedger.forEach(entry -> assertThat(entry.get("digest"))
                .as("Every ledger entry should have a digest")
                .isNotNull());
    }
}
