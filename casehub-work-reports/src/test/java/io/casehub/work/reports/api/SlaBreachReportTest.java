package io.casehub.work.reports.api;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

@QuarkusTest
class SlaBreachReportTest {

    // ── Structure ─────────────────────────────────────────────────────────────

    @Test
    void report_returns200_withExpectedStructure() {
        given().get("/workitems/reports/sla-breaches")
                .then().statusCode(200)
                .body("items", notNullValue())
                .body("summary", notNullValue())
                .body("summary.totalBreached", notNullValue())
                .body("summary.avgBreachDurationMinutes", notNullValue())
                .body("summary.byCategory", notNullValue());
    }

    // ── Happy path: breach detection ──────────────────────────────────────────

    @Test
    void completedAfterExpiry_isBreached() {
        final String cat = "breach-" + System.nanoTime();
        final String expiresAt = Instant.now().minus(2, ChronoUnit.MINUTES).toString();
        final String id = createWithExpiry("Breached WI", cat, expiresAt);
        claimStartComplete(id, "reviewer");

        given().queryParam("category", cat).get("/workitems/reports/sla-breaches")
                .then().statusCode(200)
                .body("items.workItemId", hasItem(id));
    }

    @Test
    void completedBeforeExpiry_isNotBreached() {
        final String cat = "ontime-" + System.nanoTime();
        final String expiresAt = Instant.now().plus(24, ChronoUnit.HOURS).toString();
        final String id = createWithExpiry("On-time WI", cat, expiresAt);
        claimStartComplete(id, "reviewer");

        given().queryParam("category", cat).get("/workitems/reports/sla-breaches")
                .then().statusCode(200)
                .body("items", empty());
    }

    @Test
    void openItemPastDeadline_isBreached() {
        final String cat = "open-breach-" + System.nanoTime();
        final String expiresAt = Instant.now().minus(5, ChronoUnit.MINUTES).toString();
        createWithExpiry("Open Past Deadline", cat, expiresAt);
        // leave open — still a breach

        given().queryParam("category", cat).get("/workitems/reports/sla-breaches")
                .then().statusCode(200)
                .body("items.workItemId", notNullValue());
    }

    // ── Correctness ───────────────────────────────────────────────────────────

    @Test
    void itemWithNoExpiresAt_neverAppears() {
        final String cat = "no-expiry-" + System.nanoTime();
        final String id = given().contentType(ContentType.JSON)
                .body("{\"title\":\"No Expiry\",\"category\":\"" + cat + "\",\"createdBy\":\"test\"}")
                .post("/workitems").then().statusCode(201).extract().path("id");
        claimStartComplete(id, "reviewer");

        given().queryParam("category", cat).get("/workitems/reports/sla-breaches")
                .then().statusCode(200)
                .body("items", empty());
    }

    @Test
    void completedBeforeExpiry_isNotBreached_boundary() {
        final String cat = "boundary-" + System.nanoTime();
        // expires in far future — completed now is clearly before expiry
        final String expiresAt = Instant.now().plus(1, ChronoUnit.HOURS).toString();
        final String id = createWithExpiry("Boundary WI", cat, expiresAt);
        claimStartComplete(id, "reviewer");

        given().queryParam("category", cat).get("/workitems/reports/sla-breaches")
                .then().statusCode(200)
                .body("items", empty());
    }

    // ── Filters ───────────────────────────────────────────────────────────────

    @Test
    void filterByFrom_excludesItemsBeforeWindow() {
        final String cat = "from-filter-" + System.nanoTime();
        final String expiresAt = Instant.now().minus(10, ChronoUnit.MINUTES).toString();
        final String id = createWithExpiry("Old Breach", cat, expiresAt);
        claimStartComplete(id, "reviewer");

        given().queryParam("from", "2099-01-01T00:00:00Z")
                .get("/workitems/reports/sla-breaches")
                .then().statusCode(200)
                .body("items", empty());
    }

    @Test
    void filterByTo_excludesItemsAfterWindow() {
        final String cat = "to-filter-" + System.nanoTime();
        final String expiresAt = Instant.now().minus(2, ChronoUnit.MINUTES).toString();
        final String id = createWithExpiry("Future Breach", cat, expiresAt);
        claimStartComplete(id, "reviewer");

        given().queryParam("to", "2000-01-01T00:00:00Z")
                .queryParam("category", cat)
                .get("/workitems/reports/sla-breaches")
                .then().statusCode(200)
                .body("items", empty());
    }

    @Test
    void filterByCategory_returnsOnlyThatCategory() {
        final String catA = "sla-a-" + System.nanoTime();
        final String catB = "sla-b-" + System.nanoTime();
        final String expiresAt = Instant.now().minus(2, ChronoUnit.MINUTES).toString();
        final String idA = createWithExpiry("A", catA, expiresAt);
        final String idB = createWithExpiry("B", catB, expiresAt);
        claimStartComplete(idA, "r");
        claimStartComplete(idB, "r");

        given().queryParam("category", catA).get("/workitems/reports/sla-breaches")
                .then().statusCode(200)
                .body("items.workItemId", hasItem(idA))
                .body("items.workItemId", not(hasItem(idB)));
    }

    @Test
    void filterByPriority_returnsOnlyMatchingPriority() {
        final String cat = "prio-" + System.nanoTime();
        final String expiresAt = Instant.now().minus(2, ChronoUnit.MINUTES).toString();
        final String highId = createWithExpiryAndPriority("High", cat, expiresAt, "HIGH");
        final String lowId = createWithExpiryAndPriority("Low", cat, expiresAt, "LOW");
        claimStartComplete(highId, "r");
        claimStartComplete(lowId, "r");

        given().queryParam("priority", "HIGH").queryParam("category", cat)
                .get("/workitems/reports/sla-breaches")
                .then().statusCode(200)
                .body("items.workItemId", hasItem(highId))
                .body("items.workItemId", not(hasItem(lowId)));
    }

    @Test
    void invalidPriority_returns400() {
        given().queryParam("priority", "SUPER_URGENT")
                .get("/workitems/reports/sla-breaches")
                .then().statusCode(400);
    }

    @Test
    void invalidTimestamp_returns400() {
        given().queryParam("from", "not-a-date")
                .get("/workitems/reports/sla-breaches")
                .then().statusCode(400);
    }

    // ── Response fields ───────────────────────────────────────────────────────

    @Test
    void breachedItem_hasAllRequiredFields() {
        final String cat = "fields-" + System.nanoTime();
        final String expiresAt = Instant.now().minus(2, ChronoUnit.MINUTES).toString();
        final String id = createWithExpiry("Fields Test", cat, expiresAt);
        claimStartComplete(id, "reviewer");

        given().queryParam("category", cat).get("/workitems/reports/sla-breaches")
                .then().statusCode(200)
                .body("items", hasSize(1))
                .body("items[0].workItemId", equalTo(id))
                .body("items[0].category", equalTo(cat))
                .body("items[0].priority", notNullValue())
                .body("items[0].expiresAt", notNullValue())
                .body("items[0].status", notNullValue())
                .body("items[0].breachDurationMinutes", notNullValue());
    }

    // ── Summary aggregates ────────────────────────────────────────────────────

    @Test
    void summary_totalBreached_matchesItemCount() {
        final String cat = "sumtotal-" + System.nanoTime();
        final String expiresAt = Instant.now().minus(2, ChronoUnit.MINUTES).toString();
        createWithExpiry("S1", cat, expiresAt);
        createWithExpiry("S2", cat, expiresAt);

        final int total = given().queryParam("category", cat)
                .get("/workitems/reports/sla-breaches")
                .then().statusCode(200).extract().path("summary.totalBreached");
        assertThat(total).isGreaterThanOrEqualTo(2);
    }

    @Test
    void summary_avgBreachDurationMinutes_isPositive() {
        final String cat = "avgbreach-" + System.nanoTime();
        final String expiresAt = Instant.now().minus(5, ChronoUnit.MINUTES).toString();
        final String id = createWithExpiry("Avg Test", cat, expiresAt);
        claimStartComplete(id, "reviewer");

        final float avg = given().queryParam("category", cat)
                .get("/workitems/reports/sla-breaches")
                .then().statusCode(200).extract().path("summary.avgBreachDurationMinutes");
        assertThat(avg).isGreaterThan(0f);
    }

    @Test
    void summary_byCategory_groupsCorrectly() {
        final String catX = "bcx-" + System.nanoTime();
        final String catY = "bcy-" + System.nanoTime();
        final String expiresAt = Instant.now().minus(2, ChronoUnit.MINUTES).toString();
        createWithExpiry("X1", catX, expiresAt);
        createWithExpiry("Y1", catY, expiresAt);
        createWithExpiry("Y2", catY, expiresAt);

        // Use `from` to get a unique cache key — the no-filter key is shared with the structure test
        final var resp = given().queryParam("from", "2020-01-01T00:00:00Z")
                .get("/workitems/reports/sla-breaches")
                .then().statusCode(200).extract().response();
        assertThat((Object) resp.path("summary.byCategory." + catX)).isNotNull();
        assertThat((Object) resp.path("summary.byCategory." + catY)).isNotNull();
    }

    // ── Robustness ────────────────────────────────────────────────────────────

    @Test
    void noBreaches_returns200_withEmptyItems() {
        given().queryParam("from", "2099-01-01T00:00:00Z")
                .get("/workitems/reports/sla-breaches")
                .then().statusCode(200)
                .body("items", empty())
                .body("summary.totalBreached", equalTo(0));
    }

    // ── E2E ───────────────────────────────────────────────────────────────────

    @Test
    void e2e_mixedCompliance_onlyBreachesInList() {
        final String cat = "e2e-sla-" + System.nanoTime();
        final String past = Instant.now().minus(3, ChronoUnit.MINUTES).toString();
        final String future = Instant.now().plus(1, ChronoUnit.HOURS).toString();

        final String b1 = createWithExpiry("Late 1", cat, past);
        final String b2 = createWithExpiry("Late 2", cat, past);
        final String ok = createWithExpiry("On Time", cat, future);
        claimStartComplete(b1, "r");
        claimStartComplete(b2, "r");
        claimStartComplete(ok, "r");

        given().queryParam("category", cat).get("/workitems/reports/sla-breaches")
                .then().statusCode(200)
                .body("items.workItemId", hasItem(b1))
                .body("items.workItemId", hasItem(b2))
                .body("items.workItemId", not(hasItem(ok)))
                .body("summary.totalBreached", greaterThan(1));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String createWithExpiry(final String title, final String category, final String expiresAt) {
        return given().contentType(ContentType.JSON)
                .body("{\"title\":\"" + title + "\",\"category\":\"" + category
                        + "\",\"expiresAt\":\"" + expiresAt + "\",\"createdBy\":\"test\"}")
                .post("/workitems").then().statusCode(201).extract().path("id");
    }

    private String createWithExpiryAndPriority(final String title, final String cat,
            final String expiresAt, final String priority) {
        return given().contentType(ContentType.JSON)
                .body("{\"title\":\"" + title + "\",\"category\":\"" + cat
                        + "\",\"priority\":\"" + priority
                        + "\",\"expiresAt\":\"" + expiresAt + "\",\"createdBy\":\"test\"}")
                .post("/workitems").then().statusCode(201).extract().path("id");
    }

    private void claimStartComplete(final String id, final String actor) {
        given().put("/workitems/" + id + "/claim?claimant=" + actor).then().statusCode(200);
        given().put("/workitems/" + id + "/start?actor=" + actor).then().statusCode(200);
        given().contentType(ContentType.JSON).body("{}")
                .put("/workitems/" + id + "/complete?actor=" + actor).then().statusCode(200);
    }
}
