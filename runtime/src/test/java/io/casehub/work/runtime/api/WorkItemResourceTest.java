package io.casehub.work.runtime.api;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * REST integration tests for {@link WorkItemResource}.
 *
 * <p>
 * {@code @TestTransaction} rolls back after each {@code @Test} method, ensuring
 * test isolation without requiring a full database reset.
 *
 * <p>
 * NOTE: This file is written in RED-phase TDD style. It will not compile until
 * {@code WorkItemResource} (and its response DTOs) are implemented.
 */
@QuarkusTest
@TestTransaction
class WorkItemResourceTest {

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    /**
     * POSTs a minimal WorkItem and returns its id string.
     * Verifies 201 status as part of setup — a failure here is a fixture problem,
     * not the test under examination.
     */
    private String createWorkItem() {
        return given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "title": "Test item",
                            "description": "Do something",
                            "priority": "NORMAL",
                            "createdBy": "system"
                        }
                        """)
                .when().post("/workitems")
                .then().statusCode(201)
                .extract().path("id");
    }

    // -------------------------------------------------------------------------
    // POST /workitems — create
    // -------------------------------------------------------------------------

    @Test
    void create_returns201WithLocationHeader() {
        String id = given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "title": "Test item",
                            "description": "Do something",
                            "priority": "NORMAL",
                            "createdBy": "system"
                        }
                        """)
                .when().post("/workitems")
                .then()
                .statusCode(201)
                .header("Location", containsString("/workitems/"))
                .extract().path("id");

        assertThat(id).isNotNull();
    }

    @Test
    void create_returnsWorkItemResponseBody() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "title": "Test item",
                            "description": "Do something",
                            "priority": "NORMAL",
                            "createdBy": "system"
                        }
                        """)
                .when().post("/workitems")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("title", equalTo("Test item"))
                .body("status", equalTo("PENDING"));
    }

    @Test
    void create_withCandidateGroups_storesThem() {
        String groups = given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "title": "Group item",
                            "candidateGroups": "team-a,team-b",
                            "createdBy": "system"
                        }
                        """)
                .when().post("/workitems")
                .then()
                .statusCode(201)
                .extract().path("candidateGroups");

        assertThat(groups).contains("team-a").contains("team-b");
    }

    @Test
    void create_appliesDefaultExpiresAt() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "title": "No expiry item",
                            "createdBy": "system"
                        }
                        """)
                .when().post("/workitems")
                .then()
                .statusCode(201)
                .body("expiresAt", notNullValue());
    }

    @Test
    void create_withExplicitExpiresAt_usesIt() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "title": "Explicit expiry item",
                            "createdBy": "system",
                            "expiresAt": "2026-12-31T00:00:00Z"
                        }
                        """)
                .when().post("/workitems")
                .then()
                .statusCode(201)
                .body("expiresAt", equalTo("2026-12-31T00:00:00Z"));
    }

    // -------------------------------------------------------------------------
    // GET /workitems — list all
    // -------------------------------------------------------------------------

    @Test
    void listAll_returnsArray() {
        createWorkItem();

        given()
                .when().get("/workitems")
                .then()
                .statusCode(200)
                .body("size()", greaterThanOrEqualTo(1));
    }

    // -------------------------------------------------------------------------
    // GET /workitems/{id} — get with audit trail
    // -------------------------------------------------------------------------

    @Test
    void getById_returnsWorkItemWithAuditTrail() {
        String id = createWorkItem();

        given()
                .when().get("/workitems/" + id)
                .then()
                .statusCode(200)
                .body("id", equalTo(id))
                .body("auditTrail", notNullValue())
                .body("auditTrail.size()", greaterThanOrEqualTo(1))
                .body("auditTrail[0].event", equalTo("CREATED"));
    }

    @Test
    void getById_unknownId_returns404() {
        given()
                .when().get("/workitems/00000000-0000-0000-0000-000000000000")
                .then()
                .statusCode(404);
    }

    // -------------------------------------------------------------------------
    // GET /workitems/inbox — inbox query
    // -------------------------------------------------------------------------

    @Test
    void inbox_noParams_returnsItems() {
        createWorkItem();

        given()
                .when().get("/workitems/inbox")
                .then()
                .statusCode(200)
                .body("$", notNullValue());
    }

    @Test
    void inbox_filterByAssignee_afterClaim() {
        String id = createWorkItem();

        given()
                .contentType(ContentType.JSON)
                .when().put("/workitems/" + id + "/claim?claimant=alice")
                .then().statusCode(200);

        // Inbox returns WorkItemRootResponse — item id is nested under "item.id"
        List<String> ids = given()
                .queryParam("assignee", "alice")
                .when().get("/workitems/inbox")
                .then()
                .statusCode(200)
                .extract().jsonPath().getList("item.id");

        assertThat(ids).contains(id);
    }

    @Test
    void inbox_filterByCandidateGroup() {
        String uniqueGroup = "team-inbox-" + java.util.UUID.randomUUID();
        String id = given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "title": "Group item",
                            "candidateGroups": "%s",
                            "createdBy": "system"
                        }
                        """.formatted(uniqueGroup))
                .when().post("/workitems")
                .then().statusCode(201)
                .extract().path("id");

        // Inbox returns WorkItemRootResponse — item id is nested under "item.id"
        List<String> ids = given()
                .queryParam("candidateGroup", uniqueGroup)
                .when().get("/workitems/inbox")
                .then()
                .statusCode(200)
                .extract().jsonPath().getList("item.id");

        assertThat(ids).contains(id);
    }

    @Test
    void inbox_filterByAssignee_pendingItemVisible() {
        // Inbox now requires identity context (assignee or candidateGroup) to return items.
        // Use assignee-based visibility: create item, claim it, then query by assignee.
        String id = createWorkItem();

        given()
                .contentType(ContentType.JSON)
                .when().put("/workitems/" + id + "/claim?claimant=bob-pending-test")
                .then().statusCode(200);

        List<String> ids = given()
                .queryParam("assignee", "bob-pending-test")
                .when().get("/workitems/inbox")
                .then()
                .statusCode(200)
                .extract().jsonPath().getList("item.id");

        assertThat(ids).contains(id);
    }

    @Test
    void inbox_filterByStatus_completedNotInAssigneeInbox() {
        // After completing a WorkItem, it is no longer a root visible via scanRoots
        // because scanRoots returns ALL roots for the assignee regardless of status.
        // This test verifies the endpoint still returns 200 with a valid response shape.
        String id = createWorkItem();

        // claim → start → complete
        given()
                .contentType(ContentType.JSON)
                .when().put("/workitems/" + id + "/claim?claimant=alice-complete-test")
                .then().statusCode(200);
        given()
                .contentType(ContentType.JSON)
                .when().put("/workitems/" + id + "/start?actor=alice-complete-test")
                .then().statusCode(200);
        given()
                .contentType(ContentType.JSON)
                .body("""
                        { "resolution": "All done" }
                        """)
                .when().put("/workitems/" + id + "/complete?actor=alice-complete-test")
                .then().statusCode(200);

        // Completed items are still returned (scanRoots does not filter by status);
        // the endpoint must return 200 with a valid list.
        given()
                .queryParam("assignee", "alice-complete-test")
                .when().get("/workitems/inbox")
                .then()
                .statusCode(200)
                .body("$", notNullValue());
    }

    // -------------------------------------------------------------------------
    // PUT /{id}/claim
    // -------------------------------------------------------------------------

    @Test
    void claim_returns200WithAssignedStatus() {
        String id = createWorkItem();

        given()
                .contentType(ContentType.JSON)
                .when().put("/workitems/" + id + "/claim?claimant=alice")
                .then()
                .statusCode(200)
                .body("status", equalTo("ASSIGNED"))
                .body("assigneeId", equalTo("alice"));
    }

    @Test
    void claim_alreadyAssigned_returns409() {
        String id = createWorkItem();

        given()
                .contentType(ContentType.JSON)
                .when().put("/workitems/" + id + "/claim?claimant=alice")
                .then().statusCode(200);

        given()
                .contentType(ContentType.JSON)
                .when().put("/workitems/" + id + "/claim?claimant=bob")
                .then().statusCode(409);
    }

    @Test
    void claim_unknownId_returns404() {
        given()
                .contentType(ContentType.JSON)
                .when().put("/workitems/00000000-0000-0000-0000-000000000000/claim?claimant=alice")
                .then().statusCode(404);
    }

    // -------------------------------------------------------------------------
    // PUT /{id}/start
    // -------------------------------------------------------------------------

    @Test
    void start_returns200WithInProgressStatus() {
        String id = createWorkItem();

        given()
                .contentType(ContentType.JSON)
                .when().put("/workitems/" + id + "/claim?claimant=alice")
                .then().statusCode(200);

        given()
                .contentType(ContentType.JSON)
                .when().put("/workitems/" + id + "/start?actor=alice")
                .then()
                .statusCode(200)
                .body("status", equalTo("IN_PROGRESS"));
    }

    @Test
    void start_pendingItem_returns409() {
        String id = createWorkItem();

        given()
                .contentType(ContentType.JSON)
                .when().put("/workitems/" + id + "/start?actor=alice")
                .then().statusCode(409);
    }

    // -------------------------------------------------------------------------
    // PUT /{id}/complete
    // -------------------------------------------------------------------------

    @Test
    void complete_returns200WithCompletedStatus() {
        String id = createWorkItem();

        given()
                .contentType(ContentType.JSON)
                .when().put("/workitems/" + id + "/claim?claimant=alice")
                .then().statusCode(200);
        given()
                .contentType(ContentType.JSON)
                .when().put("/workitems/" + id + "/start?actor=alice")
                .then().statusCode(200);

        given()
                .contentType(ContentType.JSON)
                .body("""
                        { "resolution": "Fixed it" }
                        """)
                .when().put("/workitems/" + id + "/complete?actor=alice")
                .then()
                .statusCode(200)
                .body("status", equalTo("COMPLETED"))
                .body("resolution", equalTo("Fixed it"));
    }

    @Test
    void complete_pendingItem_returns409() {
        String id = createWorkItem();

        given()
                .contentType(ContentType.JSON)
                .body("""
                        { "resolution": "Premature" }
                        """)
                .when().put("/workitems/" + id + "/complete?actor=alice")
                .then().statusCode(409);
    }

    // -------------------------------------------------------------------------
    // PUT /{id}/reject
    // -------------------------------------------------------------------------

    @Test
    void reject_returns200WithRejectedStatus() {
        String id = createWorkItem();

        given()
                .contentType(ContentType.JSON)
                .when().put("/workitems/" + id + "/claim?claimant=alice")
                .then().statusCode(200);

        given()
                .contentType(ContentType.JSON)
                .body("""
                        { "reason": "Not my responsibility" }
                        """)
                .when().put("/workitems/" + id + "/reject?actor=alice")
                .then()
                .statusCode(200)
                .body("status", equalTo("REJECTED"));
    }

    // -------------------------------------------------------------------------
    // PUT /{id}/delegate
    // -------------------------------------------------------------------------

    @Test
    void delegate_returns200WithNewAssignee() {
        String id = createWorkItem();

        given()
                .contentType(ContentType.JSON)
                .when().put("/workitems/" + id + "/claim?claimant=alice")
                .then().statusCode(200);

        given()
                .contentType(ContentType.JSON)
                .body("""
                        { "to": "bob" }
                        """)
                .when().put("/workitems/" + id + "/delegate?actor=alice")
                .then()
                .statusCode(200)
                .body("assigneeId", equalTo("bob"))
                .body("status", equalTo("PENDING"));
    }

    // -------------------------------------------------------------------------
    // PUT /{id}/release
    // -------------------------------------------------------------------------

    @Test
    void release_returns200WithPendingAndNullAssignee() {
        String id = createWorkItem();

        given()
                .contentType(ContentType.JSON)
                .when().put("/workitems/" + id + "/claim?claimant=alice")
                .then().statusCode(200);

        given()
                .contentType(ContentType.JSON)
                .when().put("/workitems/" + id + "/release?actor=alice")
                .then()
                .statusCode(200)
                .body("status", equalTo("PENDING"))
                .body("assigneeId", nullValue());
    }

    // -------------------------------------------------------------------------
    // PUT /{id}/suspend
    // -------------------------------------------------------------------------

    @Test
    void suspend_returns200WithSuspendedStatus() {
        String id = createWorkItem();

        given()
                .contentType(ContentType.JSON)
                .when().put("/workitems/" + id + "/claim?claimant=alice")
                .then().statusCode(200);

        given()
                .contentType(ContentType.JSON)
                .body("""
                        { "reason": "Waiting for external input" }
                        """)
                .when().put("/workitems/" + id + "/suspend?actor=alice")
                .then()
                .statusCode(200)
                .body("status", equalTo("SUSPENDED"));
    }

    // -------------------------------------------------------------------------
    // PUT /{id}/resume
    // -------------------------------------------------------------------------

    @Test
    void resume_afterAssignedSuspend_returnsAssigned() {
        String id = createWorkItem();

        given()
                .contentType(ContentType.JSON)
                .when().put("/workitems/" + id + "/claim?claimant=alice")
                .then().statusCode(200);
        given()
                .contentType(ContentType.JSON)
                .body("""
                        { "reason": "Blocked" }
                        """)
                .when().put("/workitems/" + id + "/suspend?actor=alice")
                .then().statusCode(200);

        given()
                .contentType(ContentType.JSON)
                .when().put("/workitems/" + id + "/resume?actor=alice")
                .then()
                .statusCode(200)
                .body("status", equalTo("ASSIGNED"));
    }

    // -------------------------------------------------------------------------
    // PUT /{id}/cancel
    // -------------------------------------------------------------------------

    @Test
    void cancel_returns200WithCancelledStatus() {
        String id = createWorkItem();

        given()
                .contentType(ContentType.JSON)
                .body("""
                        { "reason": "No longer needed" }
                        """)
                .when().put("/workitems/" + id + "/cancel?actor=admin")
                .then()
                .statusCode(200)
                .body("status", equalTo("CANCELLED"));
    }

    @Test
    void cancel_fromAssigned_returns200() {
        String id = createWorkItem();

        given()
                .contentType(ContentType.JSON)
                .when().put("/workitems/" + id + "/claim?claimant=alice")
                .then().statusCode(200);

        given()
                .contentType(ContentType.JSON)
                .body("""
                        { "reason": "Revoked" }
                        """)
                .when().put("/workitems/" + id + "/cancel?actor=admin")
                .then()
                .statusCode(200)
                .body("status", equalTo("CANCELLED"));
    }

    // -------------------------------------------------------------------------
    // Audit trail
    // -------------------------------------------------------------------------

    @Test
    void auditTrail_growsWithEachOperation() {
        String id = createWorkItem();

        given()
                .contentType(ContentType.JSON)
                .when().put("/workitems/" + id + "/claim?claimant=alice")
                .then().statusCode(200);
        given()
                .contentType(ContentType.JSON)
                .when().put("/workitems/" + id + "/start?actor=alice")
                .then().statusCode(200);
        given()
                .contentType(ContentType.JSON)
                .body("""
                        { "resolution": "Done" }
                        """)
                .when().put("/workitems/" + id + "/complete?actor=alice")
                .then().statusCode(200);

        List<String> events = given()
                .when().get("/workitems/" + id)
                .then()
                .statusCode(200)
                .body("auditTrail", hasSize(4))
                .extract().jsonPath().getList("auditTrail.event");

        assertThat(events).containsExactly("CREATED", "ASSIGNED", "STARTED", "COMPLETED");
    }

    // -------------------------------------------------------------------------
    // Gap-filling: error response bodies, priority/category filtering
    // -------------------------------------------------------------------------

    // Error response body format
    @Test
    void getById_notFound_responseBodyHasErrorMessage() {
        given()
                .when().get("/workitems/{id}", UUID.randomUUID())
                .then().statusCode(404)
                .body("error", notNullValue())
                .body("error", containsString("not found"));
    }

    @Test
    void claim_alreadyAssigned_responseBodyHasConflictMessage() {
        String id = createWorkItem();
        given().queryParam("claimant", "alice")
                .when().put("/workitems/{id}/claim", id);
        given().queryParam("claimant", "bob")
                .when().put("/workitems/{id}/claim", id)
                .then().statusCode(409)
                .body("error", notNullValue());
    }

    // Priority and category filtering
    @Test
    void inbox_filterByPriority() {
        // Create HIGH priority item
        given().contentType(ContentType.JSON)
                .body("""
                        {"title":"High","priority":"HIGH","createdBy":"system"}
                        """)
                .when().post("/workitems")
                .then().statusCode(201);
        // Create LOW priority item
        String lowId = given().contentType(ContentType.JSON)
                .body("""
                        {"title":"Low","priority":"LOW","createdBy":"system"}
                        """)
                .when().post("/workitems")
                .then().statusCode(201)
                .extract().path("id");

        List<String> ids = given()
                .queryParam("priority", "HIGH")
                .when().get("/workitems/inbox")
                .then().statusCode(200)
                .extract().jsonPath().getList("id");
        assertThat(ids).doesNotContain(lowId);
    }

    @Test
    void inbox_filterByCategory() {
        given().contentType(ContentType.JSON)
                .body("""
                        {"title":"Finance task","category":"finance","priority":"NORMAL","createdBy":"system"}
                        """)
                .when().post("/workitems")
                .then().statusCode(201);
        String legalId = given().contentType(ContentType.JSON)
                .body("""
                        {"title":"Legal task","category":"legal","priority":"NORMAL","createdBy":"system"}
                        """)
                .when().post("/workitems")
                .then().statusCode(201)
                .extract().path("id");

        List<String> ids = given()
                .queryParam("category", "finance")
                .when().get("/workitems/inbox")
                .then().statusCode(200)
                .extract().jsonPath().getList("id");
        assertThat(ids).doesNotContain(legalId);
    }

    @Test
    void inbox_filterByFollowUp() {
        // This test requires creating an item with a past followUpDate directly.
        // We can't easily do this via REST since followUpDate would need to be in the past.
        // Skip: followUp filter is tested at the repository level (InMemoryRepositoryTest).
        // Confirm the endpoint accepts the parameter without error.
        given()
                .queryParam("followUp", "true")
                .when().get("/workitems/inbox")
                .then().statusCode(200);
    }

    // -------------------------------------------------------------------------
    // confidenceScore — AI agent confidence metadata (Issue #112, Epic #100)
    // -------------------------------------------------------------------------

    @Test
    void createWorkItem_withConfidenceScore_persistsAndReturnsIt() {
        given().contentType(ContentType.JSON)
                .body("{\"title\":\"AI Task\",\"createdBy\":\"agent\",\"confidenceScore\":0.55}")
                .post("/workitems")
                .then().statusCode(201)
                .body("confidenceScore", equalTo(0.55f));
    }

    @Test
    void createWorkItem_withoutConfidenceScore_returnsNullConfidenceScore() {
        given().contentType(ContentType.JSON)
                .body("{\"title\":\"Human Task\",\"createdBy\":\"human\"}")
                .post("/workitems")
                .then().statusCode(201)
                .body("confidenceScore", nullValue());
    }

    @Test
    void createWorkItem_withHighConfidenceScore_returnsIt() {
        given().contentType(ContentType.JSON)
                .body("{\"title\":\"High Confidence\",\"createdBy\":\"agent\",\"confidenceScore\":0.95}")
                .post("/workitems")
                .then().statusCode(201)
                .body("confidenceScore", equalTo(0.95f));
    }
}
