package io.quarkiverse.workitems.filterregistry.action;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * Integration tests for built-in FilterActions triggered via WorkItem creation.
 * Issue #113, Epic #100.
 */
@QuarkusTest
class FilterActionIntegrationTest {

    @Test
    void applyLabelAction_addsLabelToWorkItem() {
        final String id = given().contentType(ContentType.JSON)
                .body("{\"title\":\"Label Test\",\"createdBy\":\"agent\",\"confidenceScore\":0.3}")
                .post("/workitems").then().statusCode(201).extract().path("id");

        given().get("/workitems/" + id).then().statusCode(200)
                .body("labels.path", hasItem("ai/test-label"));
    }

    @Test
    void applyLabelAction_doesNotApply_whenScoreAboveThreshold() {
        final String id = given().contentType(ContentType.JSON)
                .body("{\"title\":\"High Score\",\"createdBy\":\"agent\",\"confidenceScore\":0.8}")
                .post("/workitems").then().statusCode(201).extract().path("id");

        given().get("/workitems/" + id).then().statusCode(200)
                .body("labels.path", not(hasItem("ai/test-label")));
    }

    @Test
    void overrideCandidateGroupsAction_replacesCandidateGroups() {
        final String id = given().contentType(ContentType.JSON)
                .body("{\"title\":\"Route Test\",\"createdBy\":\"agent\"," +
                        "\"candidateGroups\":\"original-group\",\"confidenceScore\":0.2}")
                .post("/workitems").then().statusCode(201).extract().path("id");

        given().get("/workitems/" + id).then().statusCode(200)
                .body("candidateGroups", equalTo("review-team"));
    }

    @Test
    void setPriorityAction_changesPriority() {
        final String id = given().contentType(ContentType.JSON)
                .body("{\"title\":\"Priority Test\",\"createdBy\":\"agent\"," +
                        "\"priority\":\"NORMAL\",\"confidenceScore\":0.1}")
                .post("/workitems").then().statusCode(201).extract().path("id");

        given().get("/workitems/" + id).then().statusCode(200)
                .body("priority", equalTo("CRITICAL"));
    }

    @Test
    void labelAction_appliesInferredPersistence() {
        final String id = given().contentType(ContentType.JSON)
                .body("{\"title\":\"Inferred Label\",\"createdBy\":\"agent\",\"confidenceScore\":0.3}")
                .post("/workitems").then().statusCode(201).extract().path("id");

        given().get("/workitems/" + id).then().statusCode(200)
                .body("labels.findAll { it.path == 'ai/test-label' }[0].persistence",
                        equalTo("INFERRED"));
    }
}
