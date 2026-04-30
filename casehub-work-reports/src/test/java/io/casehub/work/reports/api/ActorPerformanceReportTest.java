package io.casehub.work.reports.api;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

@QuarkusTest
class ActorPerformanceReportTest {

    @Test
    void report_returns200_withExpectedStructure() {
        final String actor = "struct-" + System.nanoTime();
        given().get("/workitems/reports/actors/" + actor)
                .then().statusCode(200)
                .body("actorId", equalTo(actor))
                .body("totalAssigned", notNullValue())
                .body("totalCompleted", notNullValue())
                .body("totalRejected", notNullValue())
                .body("byCategory", notNullValue());
    }

    @Test
    void unknownActor_returns200_withZeroCounts() {
        final String actor = "nobody-" + System.nanoTime();
        given().get("/workitems/reports/actors/" + actor)
                .then().statusCode(200)
                .body("totalAssigned", equalTo(0))
                .body("totalCompleted", equalTo(0))
                .body("totalRejected", equalTo(0));
    }

    @Test
    void totalAssigned_incrementsPerClaim() {
        final String actor = "assigned-" + System.nanoTime();
        final String id1 = createWorkItem("cat1");
        final String id2 = createWorkItem("cat1");
        given().put("/workitems/" + id1 + "/claim?claimant=" + actor).then().statusCode(200);
        given().put("/workitems/" + id2 + "/claim?claimant=" + actor).then().statusCode(200);

        final int assigned = given().get("/workitems/reports/actors/" + actor)
                .then().statusCode(200).extract().path("totalAssigned");
        assertThat(assigned).isGreaterThanOrEqualTo(2);
    }

    @Test
    void totalCompleted_incrementsPerCompletion() {
        final String actor = "compl-" + System.nanoTime();
        claimStartComplete(createWorkItem("cc"), actor);
        claimStartComplete(createWorkItem("cc"), actor);

        final int completed = given().get("/workitems/reports/actors/" + actor)
                .then().statusCode(200).extract().path("totalCompleted");
        assertThat(completed).isGreaterThanOrEqualTo(2);
    }

    @Test
    void totalRejected_incrementsPerRejection() {
        final String actor = "rej-" + System.nanoTime();
        final String id = createWorkItem("rc");
        given().put("/workitems/" + id + "/claim?claimant=" + actor).then().statusCode(200);
        given().put("/workitems/" + id + "/start?actor=" + actor).then().statusCode(200);
        given().contentType(ContentType.JSON).body("{\"reason\":\"blocked\"}")
                .put("/workitems/" + id + "/reject?actor=" + actor).then().statusCode(200);

        final int rejected = given().get("/workitems/reports/actors/" + actor)
                .then().statusCode(200).extract().path("totalRejected");
        assertThat(rejected).isGreaterThanOrEqualTo(1);
    }

    @Test
    void avgCompletionMinutes_isNull_forActorWithNoCompletions() {
        final String actor = "nocomp-" + System.nanoTime();
        given().put("/workitems/" + createWorkItem("nc") + "/claim?claimant=" + actor).then().statusCode(200);

        final Object avg = given().get("/workitems/reports/actors/" + actor)
                .then().statusCode(200).extract().path("avgCompletionMinutes");
        assertThat(avg).isNull();
    }

    @Test
    void avgCompletionMinutes_isNonNegative_whenCompletionsExist() {
        final String actor = "avgactor-" + System.nanoTime();
        claimStartComplete(createWorkItem("ac"), actor);

        final float avg = given().get("/workitems/reports/actors/" + actor)
                .then().statusCode(200).extract().path("avgCompletionMinutes");
        assertThat(avg).isGreaterThanOrEqualTo(0f);
    }

    @Test
    void byCategory_countsCompletedPerCategory() {
        final String actor = "bycat-" + System.nanoTime();
        final String catA = "bca-" + System.nanoTime();
        final String catB = "bcb-" + System.nanoTime();
        claimStartComplete(createWorkItem(catA), actor);
        claimStartComplete(createWorkItem(catA), actor);
        claimStartComplete(createWorkItem(catB), actor);

        final var resp = given().get("/workitems/reports/actors/" + actor)
                .then().statusCode(200).extract().response();
        assertThat((Integer) resp.path("byCategory." + catA)).isGreaterThanOrEqualTo(2);
        assertThat((Integer) resp.path("byCategory." + catB)).isGreaterThanOrEqualTo(1);
    }

    @Test
    void filterByFrom_farFuture_returnsZeroCounts() {
        final String actor = "fromfilt-" + System.nanoTime();
        claimStartComplete(createWorkItem("fc"), actor);

        final int completed = given().queryParam("from", "2099-01-01T00:00:00Z")
                .get("/workitems/reports/actors/" + actor)
                .then().statusCode(200).extract().path("totalCompleted");
        assertThat(completed).isZero();
    }

    @Test
    void filterByTo_farPast_returnsZeroCounts() {
        final String actor = "tofilt-" + System.nanoTime();
        claimStartComplete(createWorkItem("tc"), actor);

        final int completed = given().queryParam("to", "2000-01-01T00:00:00Z")
                .get("/workitems/reports/actors/" + actor)
                .then().statusCode(200).extract().path("totalCompleted");
        assertThat(completed).isZero();
    }

    @Test
    void filterByCategory_scopesCountsToThatCategory() {
        final String actor = "catscope-" + System.nanoTime();
        final String catA = "cs-a-" + System.nanoTime();
        final String catB = "cs-b-" + System.nanoTime();
        claimStartComplete(createWorkItem(catA), actor);
        claimStartComplete(createWorkItem(catB), actor);

        final int inA = given().queryParam("category", catA)
                .get("/workitems/reports/actors/" + actor)
                .then().statusCode(200).extract().path("totalCompleted");
        assertThat(inA).isEqualTo(1);
    }

    @Test
    void e2e_fullLifecycle_allCountsCorrect() {
        final String actor = "e2eactor-" + System.nanoTime();
        final String cat = "e2ecat-" + System.nanoTime();

        claimStartComplete(createWorkItem(cat), actor);
        claimStartComplete(createWorkItem(cat), actor);

        final String rejId = createWorkItem(cat);
        given().put("/workitems/" + rejId + "/claim?claimant=" + actor).then().statusCode(200);
        given().put("/workitems/" + rejId + "/start?actor=" + actor).then().statusCode(200);
        given().contentType(ContentType.JSON).body("{\"reason\":\"blocked\"}")
                .put("/workitems/" + rejId + "/reject?actor=" + actor).then().statusCode(200);

        final String inFlightId = createWorkItem(cat);
        given().put("/workitems/" + inFlightId + "/claim?claimant=" + actor).then().statusCode(200);

        final var resp = given().get("/workitems/reports/actors/" + actor)
                .then().statusCode(200).extract().response();
        assertThat((Integer) resp.path("totalCompleted")).isGreaterThanOrEqualTo(2);
        assertThat((Integer) resp.path("totalRejected")).isGreaterThanOrEqualTo(1);
        assertThat((Integer) resp.path("totalAssigned")).isGreaterThanOrEqualTo(4);
        assertThat((Float) resp.path("avgCompletionMinutes")).isGreaterThanOrEqualTo(0f);
        assertThat((Integer) resp.path("byCategory." + cat)).isGreaterThanOrEqualTo(2);
    }

    private String createWorkItem(final String category) {
        return given().contentType(ContentType.JSON)
                .body("{\"title\":\"Perf Test\",\"category\":\"" + category + "\",\"createdBy\":\"test\"}")
                .post("/workitems").then().statusCode(201).extract().path("id");
    }

    private void claimStartComplete(final String id, final String actor) {
        given().put("/workitems/" + id + "/claim?claimant=" + actor).then().statusCode(200);
        given().put("/workitems/" + id + "/start?actor=" + actor).then().statusCode(200);
        given().contentType(ContentType.JSON).body("{}")
                .put("/workitems/" + id + "/complete?actor=" + actor).then().statusCode(200);
    }
}
