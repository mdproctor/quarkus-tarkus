package io.quarkiverse.workitems.runtime.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * Integration and E2E tests for WorkItemLink.
 *
 * <p>
 * WorkItemLinks reference external resources (design specs, policies, evidence,
 * S3 files stored elsewhere). The relationType is a pluggable string — not an enum.
 */
@QuarkusTest
class WorkItemLinkTest {

    // ── POST /workitems/{id}/links ────────────────────────────────────────────

    @Test
    void addLink_returns201_withAllFields() {
        final String itemId = createWorkItem();

        given().contentType(ContentType.JSON)
                .body("{\"url\":\"https://docs.example.com/design-spec-v2.pdf\"," +
                        "\"title\":\"Design Spec v2\",\"relationType\":\"design-spec\",\"linkedBy\":\"alice\"}")
                .post("/workitems/" + itemId + "/links")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("workItemId", equalTo(itemId))
                .body("url", equalTo("https://docs.example.com/design-spec-v2.pdf"))
                .body("title", equalTo("Design Spec v2"))
                .body("relationType", equalTo("design-spec"))
                .body("linkedBy", equalTo("alice"))
                .body("createdAt", notNullValue());
    }

    @Test
    void addLink_returns400_whenUrlBlank() {
        given().contentType(ContentType.JSON)
                .body("{\"url\":\"\",\"title\":\"Empty\",\"relationType\":\"reference\",\"linkedBy\":\"alice\"}")
                .post("/workitems/" + createWorkItem() + "/links")
                .then().statusCode(400);
    }

    @Test
    void addLink_returns400_whenRelationTypeBlank() {
        given().contentType(ContentType.JSON)
                .body("{\"url\":\"https://example.com\",\"title\":\"T\",\"relationType\":\"\",\"linkedBy\":\"alice\"}")
                .post("/workitems/" + createWorkItem() + "/links")
                .then().statusCode(400);
    }

    @Test
    void addLink_acceptsCustomRelationType_withoutRegistration() {
        final String itemId = createWorkItem();
        given().contentType(ContentType.JSON)
                .body("{\"url\":\"https://wiki.internal/page\",\"title\":\"Wiki\",\"relationType\":\"internal-wiki\",\"linkedBy\":\"bob\"}")
                .post("/workitems/" + itemId + "/links")
                .then().statusCode(201)
                .body("relationType", equalTo("internal-wiki"));
    }

    @Test
    void addLink_titleIsOptional() {
        final String itemId = createWorkItem();
        given().contentType(ContentType.JSON)
                .body("{\"url\":\"https://example.com/doc\",\"relationType\":\"reference\",\"linkedBy\":\"alice\"}")
                .post("/workitems/" + itemId + "/links")
                .then().statusCode(201)
                .body("url", equalTo("https://example.com/doc"));
    }

    // ── GET /workitems/{id}/links ─────────────────────────────────────────────

    @Test
    void listLinks_returnsEmpty_forNewWorkItem() {
        given().get("/workitems/" + createWorkItem() + "/links")
                .then().statusCode(200).body("$", empty());
    }

    @Test
    void listLinks_returnsAllLinks_chronologically() {
        final String itemId = createWorkItem();
        addLink(itemId, "https://a.example.com", "design-spec");
        addLink(itemId, "https://b.example.com", "policy");

        given().get("/workitems/" + itemId + "/links")
                .then().statusCode(200).body("$", hasSize(2));
    }

    @Test
    void listLinks_filterByType_returnsOnlyMatchingLinks() {
        final String itemId = createWorkItem();
        addLink(itemId, "https://spec.example.com", "design-spec");
        addLink(itemId, "https://policy.example.com", "policy");
        addLink(itemId, "https://ref.example.com", "design-spec");

        given().queryParam("type", "design-spec")
                .get("/workitems/" + itemId + "/links")
                .then().statusCode(200)
                .body("$", hasSize(2))
                .body("relationType", hasItem("design-spec"));
    }

    @Test
    void listLinks_filterByType_returnsEmpty_whenNoMatch() {
        final String itemId = createWorkItem();
        addLink(itemId, "https://spec.example.com", "design-spec");

        given().queryParam("type", "evidence")
                .get("/workitems/" + itemId + "/links")
                .then().statusCode(200).body("$", empty());
    }

    @Test
    void listLinks_isolatedAcrossWorkItems() {
        final String item1 = createWorkItem();
        final String item2 = createWorkItem();
        addLink(item1, "https://example.com", "reference");

        given().get("/workitems/" + item2 + "/links")
                .then().statusCode(200).body("$", empty());
    }

    // ── DELETE /workitems/{id}/links/{linkId} ─────────────────────────────────

    @Test
    void deleteLink_returns204_andLinkIsGone() {
        final String itemId = createWorkItem();
        final String linkId = given().contentType(ContentType.JSON)
                .body("{\"url\":\"https://delete.me\",\"relationType\":\"reference\",\"linkedBy\":\"alice\"}")
                .post("/workitems/" + itemId + "/links")
                .then().statusCode(201).extract().path("id");

        given().delete("/workitems/" + itemId + "/links/" + linkId)
                .then().statusCode(204);

        given().get("/workitems/" + itemId + "/links")
                .then().statusCode(200).body("$", empty());
    }

    @Test
    void deleteLink_returns404_forUnknownLink() {
        given().delete("/workitems/" + createWorkItem() + "/links/00000000-0000-0000-0000-000000000000")
                .then().statusCode(404);
    }

    @Test
    void deleteLink_onlyRemovesTargetLink() {
        final String itemId = createWorkItem();
        addLink(itemId, "https://keep.example.com", "reference");
        final String removeId = given().contentType(ContentType.JSON)
                .body("{\"url\":\"https://remove.example.com\",\"relationType\":\"policy\",\"linkedBy\":\"alice\"}")
                .post("/workitems/" + itemId + "/links")
                .then().statusCode(201).extract().path("id");

        given().delete("/workitems/" + itemId + "/links/" + removeId).then().statusCode(204);

        given().get("/workitems/" + itemId + "/links")
                .then().statusCode(200).body("$", hasSize(1))
                .body("[0].url", equalTo("https://keep.example.com"));
    }

    // ── E2E: design spec + policy + evidence on one WorkItem ─────────────────

    @Test
    void e2e_multipleTypes_filterToEach() {
        final String itemId = createWorkItem();

        addLink(itemId, "https://confluence.example.com/design-v3", "design-spec");
        addLink(itemId, "https://gov.uk/gdpr-article-22", "policy");
        addLink(itemId, "https://s3.example.com/model-output-v1.json", "evidence");

        // All three
        given().get("/workitems/" + itemId + "/links")
                .then().statusCode(200).body("$", hasSize(3));

        // Only the policy
        given().queryParam("type", "policy")
                .get("/workitems/" + itemId + "/links")
                .then().statusCode(200)
                .body("$", hasSize(1))
                .body("[0].url", equalTo("https://gov.uk/gdpr-article-22"));

        // Only evidence
        given().queryParam("type", "evidence")
                .get("/workitems/" + itemId + "/links")
                .then().statusCode(200)
                .body("$", hasSize(1))
                .body("[0].relationType", equalTo("evidence"));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String createWorkItem() {
        return given().contentType(ContentType.JSON)
                .body("{\"title\":\"Link test item\",\"createdBy\":\"test\"}")
                .post("/workitems").then().statusCode(201).extract().path("id");
    }

    private void addLink(final String itemId, final String url, final String relationType) {
        given().contentType(ContentType.JSON)
                .body("{\"url\":\"" + url + "\",\"relationType\":\"" + relationType + "\",\"linkedBy\":\"test\"}")
                .post("/workitems/" + itemId + "/links").then().statusCode(201);
    }
}
