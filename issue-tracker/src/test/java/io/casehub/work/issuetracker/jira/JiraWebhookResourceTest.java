package io.casehub.work.issuetracker.jira;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static io.restassured.RestAssured.given;

@QuarkusTest
class JiraWebhookResourceTest {

    private static final String SECRET = "test-jira-secret";
    private static final String ENDPOINT = "/workitems/jira-webhook";

    private String fixture(final String name) throws Exception {
        try (var stream = getClass().getResourceAsStream("/fixtures/jira/" + name)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void validSecret_returns200() throws Exception {
        given()
            .header("Content-Type", "application/json")
            .queryParam("secret", SECRET)
            .body(fixture("issue-resolved-done.json"))
        .when()
            .post(ENDPOINT)
        .then()
            .statusCode(200);
    }

    @Test
    void wrongSecret_returns401() throws Exception {
        given()
            .header("Content-Type", "application/json")
            .queryParam("secret", "wrong-secret")
            .body(fixture("issue-resolved-done.json"))
        .when()
            .post(ENDPOINT)
        .then()
            .statusCode(401);
    }

    @Test
    void missingSecret_returns401() throws Exception {
        given()
            .header("Content-Type", "application/json")
            .body(fixture("issue-resolved-done.json"))
        .when()
            .post(ENDPOINT)
        .then()
            .statusCode(401);
    }

    @Test
    void unhandledEvent_returns200() throws Exception {
        final String body = "{\"webhookEvent\":\"jira:issue_created\",\"issue\":{\"key\":\"PROJ-1\"}}";
        given()
            .header("Content-Type", "application/json")
            .queryParam("secret", SECRET)
            .body(body)
        .when()
            .post(ENDPOINT)
        .then()
            .statusCode(200);
    }
}
