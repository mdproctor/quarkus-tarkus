package io.quarkiverse.tarkus.runtime.api;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import java.util.List;

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
                .when().post("/tarkus/workitems")
                .then().statusCode(201)
                .extract().path("id");
    }

    // -------------------------------------------------------------------------
    // POST /tarkus/workitems — create
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
                .when().post("/tarkus/workitems")
                .then()
                .statusCode(201)
                .header("Location", containsString("/tarkus/workitems/"))
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
                .when().post("/tarkus/workitems")
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
                .when().post("/tarkus/workitems")
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
                .when().post("/tarkus/workitems")
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
                .when().post("/tarkus/workitems")
                .then()
                .statusCode(201)
                .body("expiresAt", equalTo("2026-12-31T00:00:00Z"));
    }

    // -------------------------------------------------------------------------
    // GET /tarkus/workitems — list all
    // -------------------------------------------------------------------------

    @Test
    void listAll_returnsArray() {
        createWorkItem();

        given()
                .when().get("/tarkus/workitems")
                .then()
                .statusCode(200)
                .body("size()", greaterThanOrEqualTo(1));
    }

    // -------------------------------------------------------------------------
    // GET /tarkus/workitems/{id} — get with audit trail
    // -------------------------------------------------------------------------

    @Test
    void getById_returnsWorkItemWithAuditTrail() {
        String id = createWorkItem();

        given()
                .when().get("/tarkus/workitems/" + id)
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
                .when().get("/tarkus/workitems/00000000-0000-0000-0000-000000000000")
                .then()
                .statusCode(404);
    }

    // -------------------------------------------------------------------------
    // GET /tarkus/workitems/inbox — inbox query
    // -------------------------------------------------------------------------

    @Test
    void inbox_noParams_returnsItems() {
        createWorkItem();

        given()
                .when().get("/tarkus/workitems/inbox")
                .then()
                .statusCode(200)
                .body("$", notNullValue());
    }

    @Test
    void inbox_filterByAssignee_afterClaim() {
        String id = createWorkItem();

        given()
                .contentType(ContentType.JSON)
                .when().put("/tarkus/workitems/" + id + "/claim?claimant=alice")
                .then().statusCode(200);

        List<String> ids = given()
                .queryParam("assignee", "alice")
                .when().get("/tarkus/workitems/inbox")
                .then()
                .statusCode(200)
                .extract().jsonPath().getList("id");

        assertThat(ids).contains(id);
    }

    @Test
    void inbox_filterByCandidateGroup() {
        String id = given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "title": "Group item",
                            "candidateGroups": "team-a,team-b",
                            "createdBy": "system"
                        }
                        """)
                .when().post("/tarkus/workitems")
                .then().statusCode(201)
                .extract().path("id");

        List<String> ids = given()
                .queryParam("candidateGroup", "team-a")
                .when().get("/tarkus/workitems/inbox")
                .then()
                .statusCode(200)
                .extract().jsonPath().getList("id");

        assertThat(ids).contains(id);
    }

    @Test
    void inbox_filterByStatus_pending() {
        String id = createWorkItem();

        List<String> ids = given()
                .queryParam("status", "PENDING")
                .when().get("/tarkus/workitems/inbox")
                .then()
                .statusCode(200)
                .extract().jsonPath().getList("id");

        assertThat(ids).contains(id);
    }

    @Test
    void inbox_filterByStatus_completedNotInPending() {
        String id = createWorkItem();

        // claim → start → complete
        given()
                .contentType(ContentType.JSON)
                .when().put("/tarkus/workitems/" + id + "/claim?claimant=alice")
                .then().statusCode(200);
        given()
                .contentType(ContentType.JSON)
                .when().put("/tarkus/workitems/" + id + "/start?actor=alice")
                .then().statusCode(200);
        given()
                .contentType(ContentType.JSON)
                .body("""
                        { "resolution": "All done" }
                        """)
                .when().put("/tarkus/workitems/" + id + "/complete?actor=alice")
                .then().statusCode(200);

        List<String> ids = given()
                .queryParam("status", "PENDING")
                .when().get("/tarkus/workitems/inbox")
                .then()
                .statusCode(200)
                .extract().jsonPath().getList("id");

        assertThat(ids).doesNotContain(id);
    }

    // -------------------------------------------------------------------------
    // PUT /{id}/claim
    // -------------------------------------------------------------------------

    @Test
    void claim_returns200WithAssignedStatus() {
        String id = createWorkItem();

        given()
                .contentType(ContentType.JSON)
                .when().put("/tarkus/workitems/" + id + "/claim?claimant=alice")
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
                .when().put("/tarkus/workitems/" + id + "/claim?claimant=alice")
                .then().statusCode(200);

        given()
                .contentType(ContentType.JSON)
                .when().put("/tarkus/workitems/" + id + "/claim?claimant=bob")
                .then().statusCode(409);
    }

    @Test
    void claim_unknownId_returns404() {
        given()
                .contentType(ContentType.JSON)
                .when().put("/tarkus/workitems/00000000-0000-0000-0000-000000000000/claim?claimant=alice")
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
                .when().put("/tarkus/workitems/" + id + "/claim?claimant=alice")
                .then().statusCode(200);

        given()
                .contentType(ContentType.JSON)
                .when().put("/tarkus/workitems/" + id + "/start?actor=alice")
                .then()
                .statusCode(200)
                .body("status", equalTo("IN_PROGRESS"));
    }

    @Test
    void start_pendingItem_returns409() {
        String id = createWorkItem();

        given()
                .contentType(ContentType.JSON)
                .when().put("/tarkus/workitems/" + id + "/start?actor=alice")
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
                .when().put("/tarkus/workitems/" + id + "/claim?claimant=alice")
                .then().statusCode(200);
        given()
                .contentType(ContentType.JSON)
                .when().put("/tarkus/workitems/" + id + "/start?actor=alice")
                .then().statusCode(200);

        given()
                .contentType(ContentType.JSON)
                .body("""
                        { "resolution": "Fixed it" }
                        """)
                .when().put("/tarkus/workitems/" + id + "/complete?actor=alice")
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
                .when().put("/tarkus/workitems/" + id + "/complete?actor=alice")
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
                .when().put("/tarkus/workitems/" + id + "/claim?claimant=alice")
                .then().statusCode(200);

        given()
                .contentType(ContentType.JSON)
                .body("""
                        { "reason": "Not my responsibility" }
                        """)
                .when().put("/tarkus/workitems/" + id + "/reject?actor=alice")
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
                .when().put("/tarkus/workitems/" + id + "/claim?claimant=alice")
                .then().statusCode(200);

        given()
                .contentType(ContentType.JSON)
                .body("""
                        { "to": "bob" }
                        """)
                .when().put("/tarkus/workitems/" + id + "/delegate?actor=alice")
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
                .when().put("/tarkus/workitems/" + id + "/claim?claimant=alice")
                .then().statusCode(200);

        given()
                .contentType(ContentType.JSON)
                .when().put("/tarkus/workitems/" + id + "/release?actor=alice")
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
                .when().put("/tarkus/workitems/" + id + "/claim?claimant=alice")
                .then().statusCode(200);

        given()
                .contentType(ContentType.JSON)
                .body("""
                        { "reason": "Waiting for external input" }
                        """)
                .when().put("/tarkus/workitems/" + id + "/suspend?actor=alice")
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
                .when().put("/tarkus/workitems/" + id + "/claim?claimant=alice")
                .then().statusCode(200);
        given()
                .contentType(ContentType.JSON)
                .body("""
                        { "reason": "Blocked" }
                        """)
                .when().put("/tarkus/workitems/" + id + "/suspend?actor=alice")
                .then().statusCode(200);

        given()
                .contentType(ContentType.JSON)
                .when().put("/tarkus/workitems/" + id + "/resume?actor=alice")
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
                .when().put("/tarkus/workitems/" + id + "/cancel?actor=admin")
                .then()
                .statusCode(200)
                .body("status", equalTo("CANCELLED"));
    }

    @Test
    void cancel_fromAssigned_returns200() {
        String id = createWorkItem();

        given()
                .contentType(ContentType.JSON)
                .when().put("/tarkus/workitems/" + id + "/claim?claimant=alice")
                .then().statusCode(200);

        given()
                .contentType(ContentType.JSON)
                .body("""
                        { "reason": "Revoked" }
                        """)
                .when().put("/tarkus/workitems/" + id + "/cancel?actor=admin")
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
                .when().put("/tarkus/workitems/" + id + "/claim?claimant=alice")
                .then().statusCode(200);
        given()
                .contentType(ContentType.JSON)
                .when().put("/tarkus/workitems/" + id + "/start?actor=alice")
                .then().statusCode(200);
        given()
                .contentType(ContentType.JSON)
                .body("""
                        { "resolution": "Done" }
                        """)
                .when().put("/tarkus/workitems/" + id + "/complete?actor=alice")
                .then().statusCode(200);

        List<String> events = given()
                .when().get("/tarkus/workitems/" + id)
                .then()
                .statusCode(200)
                .body("auditTrail", hasSize(4))
                .extract().jsonPath().getList("auditTrail.event");

        assertThat(events).containsExactly("CREATED", "ASSIGNED", "STARTED", "COMPLETED");
    }
}
