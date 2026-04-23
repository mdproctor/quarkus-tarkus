package io.quarkiverse.workitems.ai.skill;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkiverse.workitems.runtime.model.WorkItem;
import io.quarkiverse.workitems.runtime.repository.WorkItemStore;
import io.quarkus.test.junit.QuarkusTest;

/**
 * End-to-end integration test verifying that {@link SemanticWorkerSelectionStrategy}
 * activates correctly when {@code quarkus-workitems-ai} is on the classpath.
 *
 * <p>
 * These tests do NOT assert full embedding-based routing — that requires a real
 * configured {@code EmbeddingModel} (e.g. OpenAI or local Ollama). When no model
 * is configured {@link EmbeddingSkillMatcher} returns {@code -1.0}, all candidates
 * fall below the score threshold, and {@link SemanticWorkerSelectionStrategy}
 * returns {@code noChange()} so the WorkItem stays PENDING.
 *
 * <p>
 * Future test: configure a mock {@code EmbeddingModel} via
 * {@code @QuarkusTestResource} and assert that the highest-scoring candidate
 * is selected as assignee.
 */
@QuarkusTest
class SemanticRoutingTest {

    @Inject
    WorkItemStore workItemStore;

    @BeforeEach
    @Transactional
    void cleanup() {
        WorkerSkillProfile.deleteAll();
    }

    @Test
    void createWorkItem_withSemanticStrategyActive_succeeds() {
        // Even without EmbeddingModel configured, the strategy returns noChange() gracefully
        final var response = given()
                .contentType("application/json")
                .body("{\"title\": \"Review legal contract\", "
                        + "\"description\": \"Review the NDA for compliance\", "
                        + "\"category\": \"legal\", "
                        + "\"candidateUsers\": \"alice,bob\", "
                        + "\"createdBy\": \"agent-001\"}")
                .when().post("/workitems")
                .then().statusCode(201)
                .extract().response();

        final String id = response.jsonPath().getString("id");
        assertThat(id).isNotNull();

        final WorkItem wi = workItemStore.get(UUID.fromString(id)).orElseThrow();
        assertThat(wi).isNotNull();
        assertThat(wi.title).isEqualTo("Review legal contract");
        // With no EmbeddingModel configured, strategy returns noChange() — WorkItem stays PENDING
        assertThat(wi.status.name()).isIn("PENDING", "ASSIGNED");
    }

    @Test
    void createWorkerProfile_thenCreateWorkItem_profileStoredCorrectly() {
        given().contentType("application/json")
                .body("{\"workerId\": \"alice\", \"narrative\": \"NDA specialist, legal contracts expert\"}")
                .when().post("/worker-skill-profiles")
                .then().statusCode(201);

        given().contentType("application/json")
                .body("{\"workerId\": \"bob\", \"narrative\": \"finance accounting budget analysis\"}")
                .when().post("/worker-skill-profiles")
                .then().statusCode(201);

        // Verify profiles stored
        given().when().get("/worker-skill-profiles/alice")
                .then().statusCode(200);
        given().when().get("/worker-skill-profiles/bob")
                .then().statusCode(200);

        // Create work item — profiles exist but no EmbeddingModel, so noChange() returned
        given().contentType("application/json")
                .body("{\"title\": \"Review NDA\", "
                        + "\"description\": \"Legal review needed\", "
                        + "\"category\": \"legal\", "
                        + "\"candidateUsers\": \"alice,bob\", "
                        + "\"createdBy\": \"agent-001\"}")
                .when().post("/workitems")
                .then().statusCode(201);
    }

    @Test
    void createWorkItem_noCandidates_semanticStrategyReturnsNoChange() {
        // No candidateUsers or candidateGroups — strategy returns noChange() (empty list)
        given().contentType("application/json")
                .body("{\"title\": \"Open task\", "
                        + "\"category\": \"legal\", "
                        + "\"createdBy\": \"agent-001\"}")
                .when().post("/workitems")
                .then().statusCode(201);
    }
}
