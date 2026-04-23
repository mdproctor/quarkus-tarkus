package io.quarkiverse.workitems.examples.moderation;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;

@QuarkusTest
class ContentModerationScenarioTest {

    @Test
    @SuppressWarnings("unchecked")
    void run_contentModeration_demonstratesAgentHumanHybrid() {
        final Response response = given()
                .contentType(ContentType.JSON)
                .when()
                .post("/examples/moderation/run")
                .then()
                .statusCode(200)
                .extract().response();

        assertThat(response.jsonPath().getString("scenario")).isEqualTo("content-moderation");

        final List<Map<String, Object>> ledger = response.jsonPath().getList("ledgerEntries");

        // 4 transitions: create (agent), claim (human), start (human), reject (human)
        assertThat(ledger).hasSize(4);

        // Entry 1: created by AI agent — actorType must be AGENT
        final Map<String, Object> creationEntry = ledger.get(0);
        assertThat(creationEntry.get("commandType")).isEqualTo("CreateWorkItem");
        assertThat(creationEntry.get("actorId")).isEqualTo("agent:content-ai");
        assertThat(creationEntry.get("actorType")).isEqualTo("AGENT");

        // Entry 1: evidence from AI classifier
        assertThat(creationEntry.get("evidence")).isNotNull();
        assertThat(creationEntry.get("evidence").toString()).contains("hate-speech");
        assertThat(creationEntry.get("evidence").toString()).contains("0.73");
        assertThat(creationEntry.get("evidence").toString()).contains("mod-v3");

        // Entry 1: provenance from content-ai system
        assertThat(creationEntry.get("sourceEntitySystem")).isEqualTo("content-ai");
        assertThat(creationEntry.get("sourceEntityType")).isEqualTo("ContentFlag");

        // Rejection entry: human moderator with rationale
        final Map<String, Object> rejectionEntry = ledger.stream()
                .filter(e -> "WorkItemRejected".equals(e.get("eventType")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No rejection entry found"));
        assertThat(rejectionEntry.get("actorId")).isEqualTo("moderator-dana");
        assertThat(rejectionEntry.get("actorType")).isEqualTo("HUMAN");
        assertThat(rejectionEntry.get("rationale")).isEqualTo("Context review: satire, not hate speech");

        // Attestation on rejection entry from compliance bot (AGENT type)
        final List<Map<String, Object>> attestations = (List<Map<String, Object>>) rejectionEntry.get("attestations");
        assertThat(attestations).hasSize(1);
        assertThat(attestations.get(0).get("attestorId")).isEqualTo("agent:compliance-bot");
        assertThat(attestations.get(0).get("attestorType")).isEqualTo("AGENT");
        assertThat(attestations.get(0).get("verdict")).isEqualTo("ENDORSED");

        // All entries have digest (hash chain integrity via Merkle MMR frontier)
        ledger.forEach(entry -> assertThat(entry.get("digest"))
                .as("digest missing on entry seq=%s", entry.get("sequenceNumber"))
                .isNotNull());
    }
}
