package io.casehub.work.runtime.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * Integration and E2E tests for GET /audit — cross-WorkItem audit history query.
 *
 * <p>
 * The existing audit store writes entries on every lifecycle transition; these tests
 * drive lifecycle operations then assert on the cross-WorkItem audit query results.
 *
 * <p>
 * Issue #109, Epic #99.
 */
@QuarkusTest
class AuditResourceTest {

    // ── GET /audit — unfiltered ───────────────────────────────────────────────

    @Test
    void listAudit_returns200_withPaginatedEnvelope() {
        given().get("/audit")
                .then()
                .statusCode(200)
                .body("entries", notNullValue())
                .body("page", equalTo(0))
                .body("size", notNullValue())
                .body("total", notNullValue());
    }

    @Test
    void listAudit_includesCreatedEntries_afterWorkItemLifecycle() {
        final String actor = "audit-actor-" + System.nanoTime();
        final String id = createWorkItem("audit-cat-" + System.nanoTime(), actor);
        claimAndStart(id, actor);

        given().queryParam("actorId", actor)
                .get("/audit")
                .then()
                .statusCode(200)
                .body("entries", not(empty()))
                .body("total", greaterThanOrEqualTo(1));
    }

    @Test
    void listAudit_eachEntry_hasRequiredFields() {
        final String actor = "fields-actor-" + System.nanoTime();
        createWorkItem("fields-cat", actor);

        given().queryParam("actorId", actor)
                .get("/audit")
                .then()
                .statusCode(200)
                .body("entries[0].id", notNullValue())
                .body("entries[0].workItemId", notNullValue())
                .body("entries[0].event", notNullValue())
                .body("entries[0].occurredAt", notNullValue());
    }

    // ── Filter by actorId ─────────────────────────────────────────────────────

    @Test
    void filterByActorId_returnsOnlyThatActor() {
        final String actorA = "actor-a-" + System.nanoTime();
        final String actorB = "actor-b-" + System.nanoTime();
        createWorkItem("cat-a", actorA);
        createWorkItem("cat-b", actorB);

        given().queryParam("actorId", actorA)
                .get("/audit")
                .then()
                .statusCode(200)
                .body("entries.actor", not(hasItem(actorB)));
    }

    @Test
    void filterByActorId_returnsEmpty_forUnknownActor() {
        given().queryParam("actorId", "nobody-" + System.nanoTime())
                .get("/audit")
                .then()
                .statusCode(200)
                .body("entries", empty())
                .body("total", equalTo(0));
    }

    @Test
    void filterByActorId_capturesClaim_andStart_andComplete() {
        final String actor = "lifecycle-" + System.nanoTime();
        final String id = createWorkItem("lifecycle-cat", actor);
        claimAndStart(id, actor);
        completeWorkItem(id, actor);

        given().queryParam("actorId", actor)
                .get("/audit")
                .then()
                .statusCode(200)
                .body("entries.event", hasItem("CREATED"))
                .body("entries.event", hasItem("ASSIGNED"))
                .body("entries.event", hasItem("COMPLETED"))
                .body("total", greaterThanOrEqualTo(3));
    }

    // ── Filter by event type ──────────────────────────────────────────────────

    @Test
    void filterByEvent_returnsOnlyMatchingEventType() {
        final String actor = "ev-actor-" + System.nanoTime();
        final String id = createWorkItem("ev-cat", actor);
        claimAndStart(id, actor);
        completeWorkItem(id, actor);

        given().queryParam("event", "COMPLETED")
                .queryParam("actorId", actor)
                .get("/audit")
                .then()
                .statusCode(200)
                .body("entries.event", not(hasItem("CREATED")))
                .body("entries.event", not(hasItem("ASSIGNED")))
                .body("entries", hasSize(greaterThanOrEqualTo(1)));
    }

    @Test
    void filterByEvent_returnsEmpty_whenNoMatchingEvents() {
        final String actor = "no-rej-" + System.nanoTime();
        createWorkItem("no-rej-cat", actor);

        given().queryParam("event", "REJECTED")
                .queryParam("actorId", actor)
                .get("/audit")
                .then()
                .statusCode(200)
                .body("entries", empty());
    }

    // ── Filter by date range ──────────────────────────────────────────────────

    @Test
    void filterByFrom_excludesEntriesBeforeFrom() {
        final String actor = "range-" + System.nanoTime();
        createWorkItem("range-cat", actor);

        // from = far future: nothing should match
        given().queryParam("actorId", actor)
                .queryParam("from", "2099-01-01T00:00:00Z")
                .get("/audit")
                .then()
                .statusCode(200)
                .body("entries", empty());
    }

    @Test
    void filterByTo_excludesEntriesAfterTo() {
        final String actor = "to-range-" + System.nanoTime();
        createWorkItem("to-cat", actor);

        // to = far past: nothing should match
        given().queryParam("actorId", actor)
                .queryParam("to", "2000-01-01T00:00:00Z")
                .get("/audit")
                .then()
                .statusCode(200)
                .body("entries", empty());
    }

    @Test
    void filterByDateRange_returnsEntriesWithinRange() {
        final String actor = "within-range-" + System.nanoTime();
        createWorkItem("within-cat", actor);

        given().queryParam("actorId", actor)
                .queryParam("from", "2020-01-01T00:00:00Z")
                .queryParam("to", "2099-12-31T23:59:59Z")
                .get("/audit")
                .then()
                .statusCode(200)
                .body("entries", not(empty()))
                .body("total", greaterThanOrEqualTo(1));
    }

    // ── Filter by category ────────────────────────────────────────────────────

    @Test
    void filterByCategory_returnsOnlyEntriesForThatCategory() {
        final String cat = "audit-cat-" + System.nanoTime();
        final String otherCat = "other-cat-" + System.nanoTime();
        final String actor = "cat-filter-" + System.nanoTime();

        final String id1 = createWorkItem(cat, actor);
        final String id2 = createWorkItem(otherCat, actor);

        given().queryParam("actorId", actor)
                .queryParam("category", cat)
                .get("/audit")
                .then()
                .statusCode(200)
                .body("entries.workItemId", hasItem(id1))
                .body("entries.workItemId", not(hasItem(id2)));
    }

    // ── Pagination ────────────────────────────────────────────────────────────

    @Test
    void pagination_defaultPageIs0_defaultSizeIs20() {
        given().get("/audit")
                .then()
                .statusCode(200)
                .body("page", equalTo(0))
                .body("size", equalTo(20));
    }

    @Test
    void pagination_customPageAndSize_areReflectedInResponse() {
        given().queryParam("page", "1").queryParam("size", "5")
                .get("/audit")
                .then()
                .statusCode(200)
                .body("page", equalTo(1))
                .body("size", equalTo(5));
    }

    @Test
    void pagination_sizeCappedAt100() {
        given().queryParam("size", "999")
                .get("/audit")
                .then()
                .statusCode(200)
                .body("size", equalTo(100));
    }

    @Test
    void pagination_totalReflectsFilteredCount_notPageCount() {
        final String actor = "paged-" + System.nanoTime();
        final String id = createWorkItem("paged-cat", actor);
        claimAndStart(id, actor);
        completeWorkItem(id, actor);

        // page=0 size=1: entries has 1, but total reflects all
        final int total = given().queryParam("actorId", actor)
                .queryParam("page", "0").queryParam("size", "1")
                .get("/audit")
                .then().statusCode(200)
                .extract().path("total");

        final int entriesSize = given().queryParam("actorId", actor)
                .queryParam("page", "0").queryParam("size", "1")
                .get("/audit")
                .then().statusCode(200)
                .extract().path("entries.size()");

        // total should be >= 3 (CREATED + ASSIGNED + STARTED + COMPLETED), entries = 1
        org.assertj.core.api.Assertions.assertThat(total).isGreaterThanOrEqualTo(3);
        org.assertj.core.api.Assertions.assertThat(entriesSize).isEqualTo(1);
    }

    // ── Combined filters ──────────────────────────────────────────────────────

    @Test
    void combinedFilters_actorAndEvent_narrowsResults() {
        final String actor = "combo-" + System.nanoTime();
        final String id = createWorkItem("combo-cat", actor);
        claimAndStart(id, actor);
        completeWorkItem(id, actor);

        given().queryParam("actorId", actor)
                .queryParam("event", "COMPLETED")
                .queryParam("from", "2020-01-01T00:00:00Z")
                .queryParam("to", "2099-12-31T23:59:59Z")
                .get("/audit")
                .then()
                .statusCode(200)
                .body("entries", not(empty()))
                .body("entries.event", not(hasItem("CREATED")));
    }

    // ── E2E: compliance scenario ──────────────────────────────────────────────

    @Test
    void e2e_complianceOfficer_queriesAllWorkCompletedByAliceInQ1() {
        final String alice = "compliance-alice-" + System.nanoTime();
        final String cat = "compliance-cat-" + System.nanoTime();

        // Alice completes two WorkItems
        final String id1 = createWorkItem(cat, alice);
        claimAndStart(id1, alice);
        completeWorkItem(id1, alice);

        final String id2 = createWorkItem(cat, alice);
        claimAndStart(id2, alice);
        completeWorkItem(id2, alice);

        // Another actor completes one (should not appear in Alice's audit)
        final String bob = "compliance-bob-" + System.nanoTime();
        final String id3 = createWorkItem(cat, bob);
        claimAndStart(id3, bob);
        completeWorkItem(id3, bob);

        // Query: all COMPLETED events by Alice
        given().queryParam("actorId", alice)
                .queryParam("event", "COMPLETED")
                .get("/audit")
                .then()
                .statusCode(200)
                .body("total", equalTo(2))
                .body("entries.workItemId", hasItem(id1))
                .body("entries.workItemId", hasItem(id2))
                .body("entries.workItemId", not(hasItem(id3)));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String createWorkItem(final String category, final String createdBy) {
        return given().contentType(ContentType.JSON)
                .body("{\"title\":\"Audit Test\",\"category\":\"" + category
                        + "\",\"createdBy\":\"" + createdBy + "\"}")
                .post("/workitems")
                .then().statusCode(201).extract().path("id");
    }

    private void claimAndStart(final String id, final String actor) {
        given().put("/workitems/" + id + "/claim?claimant=" + actor).then().statusCode(200);
        given().put("/workitems/" + id + "/start?actor=" + actor).then().statusCode(200);
    }

    private void completeWorkItem(final String id, final String actor) {
        given().contentType(ContentType.JSON)
                .body("{\"resolution\":null}")
                .put("/workitems/" + id + "/complete?actor=" + actor)
                .then().statusCode(200);
    }
}
