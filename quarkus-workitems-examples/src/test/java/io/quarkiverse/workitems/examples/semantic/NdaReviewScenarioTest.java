package io.quarkiverse.workitems.examples.semantic;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import jakarta.transaction.Transactional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkiverse.workitems.ai.skill.WorkerSkillProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;

@QuarkusTest
class NdaReviewScenarioTest {

    @BeforeEach
    @Transactional
    void cleanup() {
        WorkerSkillProfile.deleteAll();
    }

    @Test
    @SuppressWarnings("unchecked")
    void run_ndaReview_routesToLegalSpecialist() {
        final Response response = given()
                .contentType(ContentType.JSON)
                .when()
                .post("/examples/semantic/run")
                .then()
                .statusCode(200)
                .extract().response();

        assertThat(response.jsonPath().getString("scenario"))
                .isEqualTo("nda-review-semantic-routing");

        // Semantic routing selected the legal specialist, not the finance analyst
        assertThat(response.jsonPath().getString("assignedTo"))
                .isEqualTo("legal-specialist");
        assertThat(response.jsonPath().getString("resolvedBy"))
                .isEqualTo("legal-specialist");

        // All 5 steps logged
        final List<Map<String, Object>> steps = response.jsonPath().getList("steps");
        assertThat(steps).hasSize(5);
        assertThat(steps.get(0).get("step")).isEqualTo(1);
        assertThat(steps.get(4).get("step")).isEqualTo(5);

        // Audit trail: CREATED → STARTED → COMPLETED (in any position)
        final List<Map<String, Object>> audit = response.jsonPath().getList("auditTrail");
        assertThat(audit).isNotEmpty();
        assertThat(audit.stream().anyMatch(e -> "CREATED".equals(e.get("event")))).isTrue();
        assertThat(audit.stream().anyMatch(e -> "STARTED".equals(e.get("event")))).isTrue();
        assertThat(audit.stream().anyMatch(e -> "COMPLETED".equals(e.get("event")))).isTrue();

        assertThat(response.jsonPath().getString("workItemId")).isNotNull();
    }
}
