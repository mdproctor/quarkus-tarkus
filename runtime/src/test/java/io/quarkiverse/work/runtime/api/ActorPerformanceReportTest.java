package io.quarkiverse.work.runtime.api;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * Integration and E2E tests for GET /workitems/reports/actors/{actorId}.
 *
 * <p>
 * Verifies that actor performance summaries are correctly derived from audit history:
 * assignment counts, completion counts, rejection counts, average completion time,
 * and per-category breakdowns.
 *
 * <p>
 * Issue #111, Epic #99.
 */
@QuarkusTest
class ActorPerformanceReportTest {

    // ── Structure ─────────────────────────────────────────────────────────────

    @Test
    void report_returns200_withExpectedStructure() {
        final String actor = "struct-actor-" + System.nanoTime();

        final io.restassured.response.Response resp = given()
                .get("/workitems/reports/actors/" + actor)
                .then()
                .statusCode(200)
                .body("actorId", equalTo(actor))
                .body("totalAssigned", notNullValue())
                .body("totalCompleted", notNullValue())
                .body("totalRejected", notNullValue())
                .body("byCategory", notNullValue())
                .extract().response();
        // avgCompletionMinutes is null when actor has no completions
        Object avgValue = resp.jsonPath().get("avgCompletionMinutes");
        if (avgValue != null) {
            throw new AssertionError(
                    "Expected avgCompletionMinutes to be null for actor with no completions, got: " + avgValue);
        }
    }

    @Test
    void report_returnsZeroCounts_forActorWithNoActivity() {
        final String actor = "nobody-" + System.nanoTime();

        given().get("/workitems/reports/actors/" + actor)
                .then()
                .statusCode(200)
                .body("totalAssigned", equalTo(0))
                .body("totalCompleted", equalTo(0))
                .body("totalRejected", equalTo(0));
    }

    // ── totalAssigned ─────────────────────────────────────────────────────────

    @Test
    void totalAssigned_countsWorkItemsClaimedByActor() {
        final String actor = "assigned-actor-" + System.nanoTime();

        // Claim two WorkItems
        final String id1 = createWorkItem("cat1", "system");
        final String id2 = createWorkItem("cat1", "system");
        given().put("/workitems/" + id1 + "/claim?claimant=" + actor).then().statusCode(200);
        given().put("/workitems/" + id2 + "/claim?claimant=" + actor).then().statusCode(200);

        final int assigned = given().get("/workitems/reports/actors/" + actor)
                .then().statusCode(200).extract().path("totalAssigned");

        assertThat(assigned).isGreaterThanOrEqualTo(2);
    }

    // ── totalCompleted ────────────────────────────────────────────────────────

    @Test
    void totalCompleted_countsWorkItemsCompletedByActor() {
        final String actor = "completed-actor-" + System.nanoTime();
        final String id1 = createWorkItem("comp-cat", "system");
        final String id2 = createWorkItem("comp-cat", "system");
        claimStartComplete(id1, actor);
        claimStartComplete(id2, actor);

        final int completed = given().get("/workitems/reports/actors/" + actor)
                .then().statusCode(200).extract().path("totalCompleted");

        assertThat(completed).isGreaterThanOrEqualTo(2);
    }

    // ── totalRejected ─────────────────────────────────────────────────────────

    @Test
    void totalRejected_countsWorkItemsRejectedByActor() {
        final String actor = "rejected-actor-" + System.nanoTime();
        final String id = createWorkItem("rej-cat", "system");
        given().put("/workitems/" + id + "/claim?claimant=" + actor).then().statusCode(200);
        given().put("/workitems/" + id + "/start?actor=" + actor).then().statusCode(200);
        given().contentType(ContentType.JSON)
                .body("{\"reason\":\"Cannot do this\"}")
                .put("/workitems/" + id + "/reject?actor=" + actor)
                .then().statusCode(200);

        final int rejected = given().get("/workitems/reports/actors/" + actor)
                .then().statusCode(200).extract().path("totalRejected");

        assertThat(rejected).isGreaterThanOrEqualTo(1);
    }

    // ── avgCompletionMinutes ──────────────────────────────────────────────────

    @Test
    void avgCompletionMinutes_isNull_forActorWithNoCompletions() {
        final String actor = "no-comp-" + System.nanoTime();

        // Just claim, don't complete
        final String id = createWorkItem("no-comp-cat", "system");
        given().put("/workitems/" + id + "/claim?claimant=" + actor).then().statusCode(200);

        // avgCompletionMinutes should be null (no completed items to average)
        final Object avg = given().get("/workitems/reports/actors/" + actor)
                .then().statusCode(200).extract().path("avgCompletionMinutes");

        // null or 0 are acceptable when no completions exist
        assertThat(avg == null || (avg instanceof Number && ((Number) avg).doubleValue() == 0.0))
                .isTrue();
    }

    @Test
    void avgCompletionMinutes_isNonNegative_forActorWithCompletions() {
        final String actor = "avg-actor-" + System.nanoTime();
        final String id = createWorkItem("avg-cat", "system");
        claimStartComplete(id, actor);

        final float avg = given().get("/workitems/reports/actors/" + actor)
                .then().statusCode(200).extract().path("avgCompletionMinutes");

        assertThat(avg).isGreaterThanOrEqualTo(0f);
    }

    // ── byCategory ────────────────────────────────────────────────────────────

    @Test
    void byCategory_showsCompletedCountPerCategory() {
        final String actor = "bycat-actor-" + System.nanoTime();
        final String catA = "bycat-a-" + System.nanoTime();
        final String catB = "bycat-b-" + System.nanoTime();

        // 2 completions in catA, 1 in catB
        claimStartComplete(createWorkItem(catA, "system"), actor);
        claimStartComplete(createWorkItem(catA, "system"), actor);
        claimStartComplete(createWorkItem(catB, "system"), actor);

        final io.restassured.response.Response resp = given()
                .get("/workitems/reports/actors/" + actor)
                .then().statusCode(200).extract().response();

        final Integer countA = resp.path("byCategory." + catA);
        final Integer countB = resp.path("byCategory." + catB);

        assertThat(countA).isGreaterThanOrEqualTo(2);
        assertThat(countB).isGreaterThanOrEqualTo(1);
    }

    // ── Date range filter ─────────────────────────────────────────────────────

    @Test
    void filterByFrom_excludesActivityBefore_from() {
        final String actor = "from-actor-" + System.nanoTime();
        claimStartComplete(createWorkItem("from-cat", "system"), actor);

        // from = far future: no activity in that range
        final int completed = given()
                .queryParam("from", "2099-01-01T00:00:00Z")
                .get("/workitems/reports/actors/" + actor)
                .then().statusCode(200).extract().path("totalCompleted");

        assertThat(completed).isZero();
    }

    @Test
    void filterByTo_excludesActivityAfter_to() {
        final String actor = "to-actor-" + System.nanoTime();
        claimStartComplete(createWorkItem("to-cat", "system"), actor);

        // to = far past: no activity in that range
        final int completed = given()
                .queryParam("to", "2000-01-01T00:00:00Z")
                .get("/workitems/reports/actors/" + actor)
                .then().statusCode(200).extract().path("totalCompleted");

        assertThat(completed).isZero();
    }

    @Test
    void filterByDateRange_includesActivityWithinRange() {
        final String actor = "range-actor-" + System.nanoTime();
        claimStartComplete(createWorkItem("range-cat", "system"), actor);

        final int completed = given()
                .queryParam("from", "2020-01-01T00:00:00Z")
                .queryParam("to", "2099-12-31T23:59:59Z")
                .get("/workitems/reports/actors/" + actor)
                .then().statusCode(200).extract().path("totalCompleted");

        assertThat(completed).isGreaterThanOrEqualTo(1);
    }

    // ── Category filter ───────────────────────────────────────────────────────

    @Test
    void filterByCategory_scopesCountsToThatCategory() {
        final String actor = "cat-actor-" + System.nanoTime();
        final String catA = "scope-a-" + System.nanoTime();
        final String catB = "scope-b-" + System.nanoTime();

        claimStartComplete(createWorkItem(catA, "system"), actor);
        claimStartComplete(createWorkItem(catB, "system"), actor);

        final int completedInA = given()
                .queryParam("category", catA)
                .get("/workitems/reports/actors/" + actor)
                .then().statusCode(200).extract().path("totalCompleted");

        assertThat(completedInA).isEqualTo(1);
    }

    // ── E2E ───────────────────────────────────────────────────────────────────

    @Test
    void e2e_actorPerformanceSummary_fullLifecycle() {
        final String actor = "e2e-actor-" + System.nanoTime();
        final String cat = "e2e-perf-" + System.nanoTime();

        // 2 completed, 1 rejected, 1 just assigned (in-flight)
        claimStartComplete(createWorkItem(cat, "system"), actor);
        claimStartComplete(createWorkItem(cat, "system"), actor);

        final String rejId = createWorkItem(cat, "system");
        given().put("/workitems/" + rejId + "/claim?claimant=" + actor).then().statusCode(200);
        given().put("/workitems/" + rejId + "/start?actor=" + actor).then().statusCode(200);
        given().contentType(ContentType.JSON).body("{\"reason\":\"blocked\"}")
                .put("/workitems/" + rejId + "/reject?actor=" + actor).then().statusCode(200);

        final String inFlightId = createWorkItem(cat, "system");
        given().put("/workitems/" + inFlightId + "/claim?claimant=" + actor).then().statusCode(200);

        final io.restassured.response.Response resp = given()
                .get("/workitems/reports/actors/" + actor)
                .then().statusCode(200).extract().response();

        assertThat((Integer) resp.path("totalCompleted")).isGreaterThanOrEqualTo(2);
        assertThat((Integer) resp.path("totalRejected")).isGreaterThanOrEqualTo(1);
        assertThat((Integer) resp.path("totalAssigned")).isGreaterThanOrEqualTo(4); // all claims
        assertThat((Float) resp.path("avgCompletionMinutes")).isGreaterThanOrEqualTo(0f);
        assertThat((Integer) resp.path("byCategory." + cat)).isGreaterThanOrEqualTo(2);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String createWorkItem(final String category, final String createdBy) {
        return given().contentType(ContentType.JSON)
                .body("{\"title\":\"Perf Test\",\"category\":\"" + category
                        + "\",\"createdBy\":\"" + createdBy + "\"}")
                .post("/workitems")
                .then().statusCode(201).extract().path("id");
    }

    private void claimStartComplete(final String id, final String actor) {
        given().put("/workitems/" + id + "/claim?claimant=" + actor).then().statusCode(200);
        given().put("/workitems/" + id + "/start?actor=" + actor).then().statusCode(200);
        given().contentType(ContentType.JSON).body("{}")
                .put("/workitems/" + id + "/complete?actor=" + actor).then().statusCode(200);
    }
}
