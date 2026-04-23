package io.quarkiverse.work.ai;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * Integration + E2E tests for confidence-gated routing.
 * Issue #114, Epic #100.
 */
@QuarkusTest
class LowConfidenceFilterTest {

    @Test
    void lowConfidence_belowThreshold_appliesAiLabel() {
        final String id = createWithScore(0.55);
        given().get("/workitems/" + id).then().statusCode(200)
                .body("labels.path", hasItem("ai/low-confidence"));
    }

    @Test
    void highConfidence_aboveThreshold_noAiLabel() {
        final String id = createWithScore(0.85);
        given().get("/workitems/" + id).then().statusCode(200)
                .body("labels.path", not(hasItem("ai/low-confidence")));
    }

    @Test
    void exactlyAtThreshold_noAiLabel() {
        // 0.7 is NOT < 0.7
        final String id = createWithScore(0.7);
        given().get("/workitems/" + id).then().statusCode(200)
                .body("labels.path", not(hasItem("ai/low-confidence")));
    }

    @Test
    void nullConfidenceScore_noAiLabel() {
        final String id = given().contentType(ContentType.JSON)
                .body("{\"title\":\"Human Task\",\"createdBy\":\"human\"}")
                .post("/workitems").then().statusCode(201).extract().path("id");
        given().get("/workitems/" + id).then().statusCode(200)
                .body("labels.path", not(hasItem("ai/low-confidence")));
    }

    @Test
    void lowConfidence_labelIsInferred_notManual() {
        final String id = createWithScore(0.3);
        given().get("/workitems/" + id).then().statusCode(200)
                .body("labels.findAll { it.path == 'ai/low-confidence' }[0].persistence",
                        equalTo("INFERRED"));
    }

    @Test
    void lowConfidenceFilter_appearsInPermanentFilterList() {
        given().get("/filter-rules/permanent").then().statusCode(200)
                .body("name", hasItem("ai/low-confidence"));
    }

    @Test
    void confidenceScore_isStoredAndReturnedInResponse() {
        final String id = createWithScore(0.42);
        given().get("/workitems/" + id).then().statusCode(200)
                .body("confidenceScore", equalTo(0.42f));
    }

    @Test
    void e2e_aiAgentCreatesLowConfidenceItem_reviewerSeeItInInbox() {
        final String id = given().contentType(ContentType.JSON)
                .body("{\"title\":\"Uncertain Decision\",\"category\":\"risk\"," +
                        "\"candidateGroups\":\"analysts\",\"createdBy\":\"agent:risk-ai\"," +
                        "\"confidenceScore\":0.42}")
                .post("/workitems").then().statusCode(201).extract().path("id");

        given().get("/workitems/" + id).then().statusCode(200)
                .body("labels.path", hasItem("ai/low-confidence"))
                .body("confidenceScore", equalTo(0.42f));

        // Reviewer queries by label pattern
        given().queryParam("label", "ai/*").get("/workitems")
                .then().statusCode(200)
                .body("id", hasItem(id));

        // Full lifecycle: claim → start → complete
        given().put("/workitems/" + id + "/claim?claimant=reviewer").then().statusCode(200);
        given().put("/workitems/" + id + "/start?actor=reviewer").then().statusCode(200);
        given().contentType(ContentType.JSON).body("{}")
                .put("/workitems/" + id + "/complete?actor=reviewer").then().statusCode(200)
                .body("status", equalTo("COMPLETED"));
    }

    private String createWithScore(final double score) {
        return given().contentType(ContentType.JSON)
                .body("{\"title\":\"AI Task\",\"createdBy\":\"agent\",\"confidenceScore\":" + score + "}")
                .post("/workitems").then().statusCode(201).extract().path("id");
    }
}
