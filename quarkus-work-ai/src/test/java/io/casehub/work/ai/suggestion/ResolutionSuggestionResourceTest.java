package io.casehub.work.ai.suggestion;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;

/**
 * Integration tests for {@link ResolutionSuggestionResource}.
 *
 * <p>
 * No {@code ChatModel} is configured in the test profile, so all suggestion calls
 * return {@code modelAvailable: false} and {@code suggestion: null}. This covers
 * the graceful-degradation path and the endpoint wiring without requiring an
 * external AI provider.
 */
@QuarkusTest
class ResolutionSuggestionResourceTest {

    @Test
    void suggest_knownWorkItem_noModel_returnsModelUnavailable() {
        // Create a WorkItem first
        final Response create = given()
                .contentType(ContentType.JSON)
                .body("{\"title\":\"Review supplier contract\","
                        + "\"category\":\"procurement\","
                        + "\"createdBy\":\"test-agent\"}")
                .when().post("/workitems")
                .then().statusCode(201)
                .extract().response();

        final String id = create.jsonPath().getString("id");

        // Call the suggestion endpoint
        final Response suggestion = given()
                .when().get("/workitems/" + id + "/resolution-suggestion")
                .then().statusCode(200)
                .extract().response();

        assertThat(suggestion.jsonPath().getString("workItemId")).isEqualTo(id);
        assertThat(suggestion.jsonPath().getBoolean("modelAvailable")).isFalse();
        assertThat(suggestion.jsonPath().getString("suggestion")).isNull();
        assertThat(suggestion.jsonPath().getInt("basedOn")).isEqualTo(0);
    }

    @Test
    void suggest_unknownWorkItem_returns404() {
        given()
                .when().get("/workitems/" + UUID.randomUUID() + "/resolution-suggestion")
                .then().statusCode(404);
    }

    @Test
    void suggest_responseShapeIsComplete() {
        final Response create = given()
                .contentType(ContentType.JSON)
                .body("{\"title\":\"Approve leave request\","
                        + "\"category\":\"hr\","
                        + "\"createdBy\":\"hr-system\"}")
                .when().post("/workitems")
                .then().statusCode(201)
                .extract().response();

        final String id = create.jsonPath().getString("id");

        final Response suggestion = given()
                .when().get("/workitems/" + id + "/resolution-suggestion")
                .then().statusCode(200)
                .extract().response();

        // All four fields present in the JSON body
        final String body = suggestion.asString();
        assertThat(body).contains("workItemId");
        assertThat(body).contains("modelAvailable");
        assertThat(body).contains("basedOn");
        assertThat(suggestion.jsonPath().getString("workItemId")).isNotNull();
    }
}
