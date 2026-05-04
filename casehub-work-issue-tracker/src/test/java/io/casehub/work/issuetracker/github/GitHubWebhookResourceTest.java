package io.casehub.work.issuetracker.github;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static io.restassured.RestAssured.given;

@QuarkusTest
class GitHubWebhookResourceTest {

    private static final String SECRET = "test-webhook-secret";
    private static final String ENDPOINT = "/workitems/github-webhook";

    private String sign(final String body) throws Exception {
        final Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }

    private String fixture(final String name) throws Exception {
        try (var stream = getClass().getResourceAsStream("/fixtures/github/" + name)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void validSignature_returns200() throws Exception {
        final String body = fixture("issue-closed-completed.json");
        given()
            .header("Content-Type", "application/json")
            .header("X-Hub-Signature-256", sign(body))
            .body(body)
        .when()
            .post(ENDPOINT)
        .then()
            .statusCode(200);
    }

    @Test
    void invalidSignature_returns401() throws Exception {
        final String body = fixture("issue-closed-completed.json");
        given()
            .header("Content-Type", "application/json")
            .header("X-Hub-Signature-256", "sha256=deadbeef")
            .body(body)
        .when()
            .post(ENDPOINT)
        .then()
            .statusCode(401);
    }

    @Test
    void missingSignatureHeader_returns401() throws Exception {
        final String body = fixture("issue-closed-completed.json");
        given()
            .header("Content-Type", "application/json")
            .body(body)
        .when()
            .post(ENDPOINT)
        .then()
            .statusCode(401);
    }

    @Test
    void unhandledAction_returns200() throws Exception {
        final String body = """
            {"action":"reopened","issue":{"number":1,"title":"T","body":"B",
             "state":"open","state_reason":null,"html_url":"https://github.com/o/r/issues/1",
             "assignee":null,"labels":[]},"repository":{"full_name":"o/r"},
             "sender":{"login":"alice"}}
            """;
        given()
            .header("Content-Type", "application/json")
            .header("X-Hub-Signature-256", sign(body))
            .body(body)
        .when()
            .post(ENDPOINT)
        .then()
            .statusCode(200);
    }
}
