package io.quarkiverse.workitems.issuetracker.api;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkiverse.workitems.issuetracker.StubIssueTrackerProvider;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

@QuarkusTest
class IssueLinkResourceTest {

    @Inject
    StubIssueTrackerProvider stub;

    @BeforeEach
    void reset() {
        // Tests use unique workItemId UUIDs so cross-test contamination is impossible;
        // deleteAll() requires a transaction which REST tests don't have.
        stub.reset();
    }

    // ── POST /workitems/{id}/issues ───────────────────────────────────────────

    @Test
    void linkIssue_returns201_withLinkDetails() {
        stub.seed("owner/repo#42", "Fix the null pointer", "open");
        final String workItemId = UUID.randomUUID().toString();

        given().contentType(ContentType.JSON)
                .body("""
                        {"trackerType":"github","externalRef":"owner/repo#42","linkedBy":"alice"}
                        """)
                .post("/workitems/" + workItemId + "/issues")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("trackerType", equalTo("github"))
                .body("externalRef", equalTo("owner/repo#42"))
                .body("title", equalTo("Fix the null pointer"))
                .body("status", equalTo("open"))
                .body("linkedBy", equalTo("alice"));
    }

    @Test
    void linkIssue_isIdempotent_returns201BothTimes() {
        stub.seed("owner/repo#55", "Idempotent link", "open");
        final String workItemId = UUID.randomUUID().toString();
        final String body = """
                {"trackerType":"github","externalRef":"owner/repo#55","linkedBy":"alice"}
                """;

        given().contentType(ContentType.JSON).body(body)
                .post("/workitems/" + workItemId + "/issues").then().statusCode(201);
        given().contentType(ContentType.JSON).body(body)
                .post("/workitems/" + workItemId + "/issues").then().statusCode(201);

        // Verify only one link via the REST API (unique workItemId per test)
        given().get("/workitems/" + workItemId + "/issues")
                .then().statusCode(200).body("$", hasSize(1));
    }

    @Test
    void linkIssue_returns404_whenIssueNotFound() {
        final String workItemId = UUID.randomUUID().toString();

        given().contentType(ContentType.JSON)
                .body("""
                        {"trackerType":"github","externalRef":"owner/repo#99999","linkedBy":"alice"}
                        """)
                .post("/workitems/" + workItemId + "/issues")
                .then()
                .statusCode(404);
    }

    @Test
    void linkIssue_returns400_whenMissingRequiredFields() {
        given().contentType(ContentType.JSON)
                .body("{\"trackerType\":\"github\"}")
                .post("/workitems/" + UUID.randomUUID() + "/issues")
                .then()
                .statusCode(400);
    }

    @Test
    void linkIssue_returns400_forUnknownTrackerType() {
        given().contentType(ContentType.JSON)
                .body("""
                        {"trackerType":"jira","externalRef":"PROJ-1","linkedBy":"alice"}
                        """)
                .post("/workitems/" + UUID.randomUUID() + "/issues")
                .then()
                .statusCode(400)
                .body("error", org.hamcrest.Matchers.containsString("jira"));
    }

    // ── POST /workitems/{id}/issues/create ────────────────────────────────────

    @Test
    void createAndLink_returns201_withNewIssueRef() {
        final String workItemId = UUID.randomUUID().toString();

        final String ref = given().contentType(ContentType.JSON)
                .body("""
                        {"trackerType":"github","title":"Auto-created issue",
                         "body":"Details here","linkedBy":"system"}
                        """)
                .post("/workitems/" + workItemId + "/issues/create")
                .then()
                .statusCode(201)
                .body("status", equalTo("open"))
                .extract().path("externalRef");

        assertThat(ref).startsWith("stub/repo#");
        assertThat(stub.created()).contains(ref);
    }

    // ── GET /workitems/{id}/issues ────────────────────────────────────────────

    @Test
    void listLinks_returns200_withAllLinks() {
        stub.seed("owner/repo#1", "Issue 1", "open");
        stub.seed("owner/repo#2", "Issue 2", "closed");
        final String workItemId = UUID.randomUUID().toString();

        given().contentType(ContentType.JSON)
                .body("{\"trackerType\":\"github\",\"externalRef\":\"owner/repo#1\",\"linkedBy\":\"alice\"}")
                .post("/workitems/" + workItemId + "/issues").then().statusCode(201);
        given().contentType(ContentType.JSON)
                .body("{\"trackerType\":\"github\",\"externalRef\":\"owner/repo#2\",\"linkedBy\":\"alice\"}")
                .post("/workitems/" + workItemId + "/issues").then().statusCode(201);

        given().get("/workitems/" + workItemId + "/issues")
                .then()
                .statusCode(200)
                .body("$", hasSize(2));
    }

    @Test
    void listLinks_returnsEmpty_forNewWorkItem() {
        given().get("/workitems/" + UUID.randomUUID() + "/issues")
                .then()
                .statusCode(200)
                .body("$", hasSize(0));
    }

    // ── DELETE /workitems/{id}/issues/{linkId} ────────────────────────────────

    @Test
    void removeLink_returns204_onSuccess() {
        stub.seed("owner/repo#9", "Removable", "open");
        final String workItemId = UUID.randomUUID().toString();

        final String linkId = given().contentType(ContentType.JSON)
                .body("{\"trackerType\":\"github\",\"externalRef\":\"owner/repo#9\",\"linkedBy\":\"alice\"}")
                .post("/workitems/" + workItemId + "/issues")
                .then().statusCode(201)
                .extract().path("id");

        given().delete("/workitems/" + workItemId + "/issues/" + linkId)
                .then()
                .statusCode(204);

        given().get("/workitems/" + workItemId + "/issues")
                .then().statusCode(200).body("$", hasSize(0));
    }

    @Test
    void removeLink_returns404_whenNotFound() {
        given().delete("/workitems/" + UUID.randomUUID() + "/issues/" + UUID.randomUUID())
                .then()
                .statusCode(404);
    }

    // ── PUT /workitems/{id}/issues/sync ───────────────────────────────────────

    @Test
    void syncLinks_returns200_withSyncCount() {
        stub.seed("owner/repo#20", "Sync me", "open");
        final String workItemId = UUID.randomUUID().toString();

        given().contentType(ContentType.JSON)
                .body("{\"trackerType\":\"github\",\"externalRef\":\"owner/repo#20\",\"linkedBy\":\"alice\"}")
                .post("/workitems/" + workItemId + "/issues").then().statusCode(201);

        stub.seed("owner/repo#20", "Sync me", "closed");

        given().put("/workitems/" + workItemId + "/issues/sync")
                .then()
                .statusCode(200)
                .body("synced", equalTo(1));

        given().get("/workitems/" + workItemId + "/issues")
                .then()
                .body("[0].status", equalTo("closed"));
    }
}
