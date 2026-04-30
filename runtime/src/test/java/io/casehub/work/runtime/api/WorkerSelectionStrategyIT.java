package io.casehub.work.runtime.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * Integration and E2E tests for WorkerSelectionStrategy wired into WorkItem lifecycle.
 * Tests use dynamic actor IDs (nanoTime suffix) to avoid cross-test interference.
 * Issues #115/#116, Epics #100/#102.
 */
@QuarkusTest
class WorkerSelectionStrategyIT {

    // ── Least-loaded (default) — pre-assignment ───────────────────────────────

    @Test
    void leastLoaded_preAssigns_toCandidateUser_withLowestActiveCount() {
        final String alice = "ls-alice-" + System.nanoTime();
        final String bob = "ls-bob-" + System.nanoTime();
        // alice gets 2 active WorkItems
        createAndStartWorkItem(alice);
        createAndStartWorkItem(alice);
        // bob has 0

        final String id = given().contentType(ContentType.JSON)
                .body("{\"title\":\"Route Me\",\"createdBy\":\"system\"," +
                        "\"candidateUsers\":\"" + alice + "," + bob + "\"}")
                .post("/workitems").then().statusCode(201)
                .extract().path("id");

        given().get("/workitems/" + id).then().statusCode(200)
                .body("assigneeId", equalTo(bob))
                .body("status", equalTo("ASSIGNED"));
    }

    @Test
    void leastLoaded_noPreAssignment_whenNoCandidates() {
        final String id = given().contentType(ContentType.JSON)
                .body("{\"title\":\"No Candidates\",\"createdBy\":\"system\"}")
                .post("/workitems").then().statusCode(201)
                .extract().path("id");

        given().get("/workitems/" + id).then().statusCode(200)
                .body("assigneeId", nullValue())
                .body("status", equalTo("PENDING"));
    }

    @Test
    void leastLoaded_preAssigns_toSingleCandidate() {
        final String actor = "solo-" + System.nanoTime();
        final String id = given().contentType(ContentType.JSON)
                .body("{\"title\":\"Solo\",\"createdBy\":\"system\"," +
                        "\"candidateUsers\":\"" + actor + "\"}")
                .post("/workitems").then().statusCode(201)
                .extract().path("id");

        given().get("/workitems/" + id).then().statusCode(200)
                .body("assigneeId", equalTo(actor))
                .body("status", equalTo("ASSIGNED"));
    }

    @Test
    void preAssigned_workItem_isAlreadyAssigned_noClaimNeeded() {
        final String actor = "no-claim-" + System.nanoTime();
        final String id = given().contentType(ContentType.JSON)
                .body("{\"title\":\"Pre-Assigned\",\"createdBy\":\"system\"," +
                        "\"candidateUsers\":\"" + actor + "\"}")
                .post("/workitems").then().statusCode(201)
                .extract().path("id");

        // Should be ASSIGNED without any PUT /claim call
        given().get("/workitems/" + id).then().statusCode(200)
                .body("status", equalTo("ASSIGNED"))
                .body("assigneeId", equalTo(actor));

        // Can start immediately
        given().put("/workitems/" + id + "/start?actor=" + actor)
                .then().statusCode(200)
                .body("status", equalTo("IN_PROGRESS"));
    }

    // ── candidateGroups with NoOpWorkerRegistry → PENDING ────────────────────

    @Test
    void candidateGroups_withNoOpRegistry_workItemStaysPending() {
        final String id = given().contentType(ContentType.JSON)
                .body("{\"title\":\"Group Only\",\"createdBy\":\"system\"," +
                        "\"candidateGroups\":\"some-group\"}")
                .post("/workitems").then().statusCode(201)
                .extract().path("id");

        given().get("/workitems/" + id).then().statusCode(200)
                .body("status", equalTo("PENDING"))
                .body("assigneeId", nullValue());
    }

    // ── requiredCapabilities → candidateUsers have no caps → PENDING ──────────

    @Test
    void requiredCapabilities_candidateUsersHaveNoCaps_workItemStaysPending() {
        final String actor = "cap-actor-" + System.nanoTime();
        final String id = given().contentType(ContentType.JSON)
                .body("{\"title\":\"Cap Required\",\"createdBy\":\"system\"," +
                        "\"candidateUsers\":\"" + actor + "\"," +
                        "\"requiredCapabilities\":\"exotic-skill\"}")
                .post("/workitems").then().statusCode(201)
                .extract().path("id");

        given().get("/workitems/" + id).then().statusCode(200)
                .body("status", equalTo("PENDING"))
                .body("assigneeId", nullValue());
    }

    // ── RELEASED trigger ──────────────────────────────────────────────────────

    @Test
    void leastLoaded_refiresOnRelease_andReassigns() {
        final String alice = "rel-alice-" + System.nanoTime();
        final String bob = "rel-bob-" + System.nanoTime();

        // First WorkItem — alice has 0, bob has 0 → alice gets it (first in list)
        final String id = given().contentType(ContentType.JSON)
                .body("{\"title\":\"Release Me\",\"createdBy\":\"system\"," +
                        "\"candidateUsers\":\"" + alice + "," + bob + "\"}")
                .post("/workitems").then().statusCode(201)
                .extract().path("id");

        // Whoever was assigned, give bob 3 extra active items
        createAndStartWorkItem(bob);
        createAndStartWorkItem(bob);
        createAndStartWorkItem(bob);

        // Release → strategy re-fires → alice should win (fewer active)
        given().put("/workitems/" + id + "/release?actor=" + alice).then().statusCode(200)
                .body("assigneeId", equalTo(alice))
                .body("status", equalTo("ASSIGNED"));
    }

    // ── DELEGATED trigger ─────────────────────────────────────────────────────

    @Test
    void leastLoaded_refiresOnDelegate_andReassigns() {
        final String alice = "del-alice-" + System.nanoTime();
        final String bob = "del-bob-" + System.nanoTime();
        final String carol = "del-carol-" + System.nanoTime();

        final String id = given().contentType(ContentType.JSON)
                .body("{\"title\":\"Delegate Me\",\"createdBy\":\"system\"," +
                        "\"candidateUsers\":\"" + alice + "," + bob + "," + carol + "\"}")
                .post("/workitems").then().statusCode(201)
                .extract().path("id");

        // Start it (required to delegate)
        final String assignee = given().get("/workitems/" + id).then().statusCode(200)
                .extract().path("assigneeId");
        given().put("/workitems/" + id + "/start?actor=" + assignee).then().statusCode(200);

        // Give bob 3 active items, carol 0
        createAndStartWorkItem(bob);
        createAndStartWorkItem(bob);
        createAndStartWorkItem(bob);

        // Delegate → strategy re-fires → carol should win (0 active)
        given().contentType(ContentType.JSON)
                .body("{\"to\":\"" + carol + "\"}")
                .put("/workitems/" + id + "/delegate?actor=" + assignee)
                .then().statusCode(200)
                .body("assigneeId", equalTo(carol));
    }

    // ── E2E: full lifecycle with pre-assignment ───────────────────────────────

    @Test
    void e2e_preAssigned_fullLifecycle_noClaimRequired() {
        final String alice = "e2e-alice-" + System.nanoTime();
        final String bob = "e2e-bob-" + System.nanoTime();

        // alice has 2 active, bob has 0 → bob gets the new WorkItem
        createAndStartWorkItem(alice);
        createAndStartWorkItem(alice);

        final String id = given().contentType(ContentType.JSON)
                .body("{\"title\":\"E2E Work\",\"createdBy\":\"agent:ai\"," +
                        "\"candidateUsers\":\"" + alice + "," + bob + "\"," +
                        "\"confidenceScore\":0.9}")
                .post("/workitems").then().statusCode(201)
                .body("assigneeId", equalTo(bob))
                .body("status", equalTo("ASSIGNED"))
                .body("confidenceScore", equalTo(0.9f))
                .extract().path("id");

        // Bob starts immediately (no claim step)
        given().put("/workitems/" + id + "/start?actor=" + bob)
                .then().statusCode(200).body("status", equalTo("IN_PROGRESS"));

        // Bob completes
        given().contentType(ContentType.JSON).body("{\"resolution\":null}")
                .put("/workitems/" + id + "/complete?actor=" + bob)
                .then().statusCode(200)
                .body("status", equalTo("COMPLETED"))
                .body("assigneeId", equalTo(bob));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Creates a WorkItem with only the given actor as candidateUser (so it gets pre-assigned
     * to that actor by LeastLoadedStrategy) then starts it to put it in IN_PROGRESS state.
     * This is the reliable way to add an active WorkItem to an actor's workload count.
     */
    private void createAndStartWorkItem(final String actor) {
        final String id = given().contentType(ContentType.JSON)
                .body("{\"title\":\"Active\",\"createdBy\":\"system\"," +
                        "\"candidateUsers\":\"" + actor + "\"}")
                .post("/workitems").then().statusCode(201).extract().path("id");

        // Pre-assigned to actor by least-loaded (only candidate) → ASSIGNED
        // Start to put in IN_PROGRESS (counted as active)
        given().put("/workitems/" + id + "/start?actor=" + actor).then().statusCode(200);
    }
}
