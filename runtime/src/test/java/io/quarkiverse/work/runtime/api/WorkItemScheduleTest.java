package io.quarkiverse.work.runtime.api;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.quarkiverse.work.runtime.service.WorkItemScheduleService;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * Integration and E2E tests for WorkItemSchedule.
 */
@QuarkusTest
class WorkItemScheduleTest {

    @Inject
    WorkItemScheduleService scheduleService;

    // ── POST /workitem-schedules ──────────────────────────────────────────────

    @Test
    void createSchedule_returns201_withAllFields() {
        final String templateId = createTemplate();

        given().contentType(ContentType.JSON)
                .body("{\"name\":\"Daily compliance check\",\"templateId\":\"" + templateId
                        + "\",\"cronExpression\":\"0 0 9 * * ?\",\"createdBy\":\"admin\"}")
                .post("/workitem-schedules")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("name", equalTo("Daily compliance check"))
                .body("templateId", equalTo(templateId))
                .body("cronExpression", equalTo("0 0 9 * * ?"))
                .body("active", equalTo(true))
                .body("nextFireAt", notNullValue())
                .body("createdBy", equalTo("admin"));
    }

    @Test
    void createSchedule_returns400_whenNameMissing() {
        given().contentType(ContentType.JSON)
                .body("{\"templateId\":\"" + createTemplate()
                        + "\",\"cronExpression\":\"0 0 9 * * ?\",\"createdBy\":\"admin\"}")
                .post("/workitem-schedules")
                .then().statusCode(400);
    }

    @Test
    void createSchedule_returns400_whenInvalidCron() {
        given().contentType(ContentType.JSON)
                .body("{\"name\":\"Bad cron\",\"templateId\":\"" + createTemplate()
                        + "\",\"cronExpression\":\"not-valid-cron\",\"createdBy\":\"admin\"}")
                .post("/workitem-schedules")
                .then().statusCode(400)
                .body("error", org.hamcrest.Matchers.containsString("cron"));
    }

    // ── GET /workitem-schedules ───────────────────────────────────────────────

    @Test
    void listSchedules_includesCreated() {
        final String templateId = createTemplate();
        given().contentType(ContentType.JSON)
                .body("{\"name\":\"List test schedule\",\"templateId\":\"" + templateId
                        + "\",\"cronExpression\":\"0 0 8 * * ?\",\"createdBy\":\"admin\"}")
                .post("/workitem-schedules").then().statusCode(201);

        given().get("/workitem-schedules")
                .then().statusCode(200)
                .body("name", hasItem("List test schedule"));
    }

    // ── GET /workitem-schedules/{id} ──────────────────────────────────────────

    @Test
    void getSchedule_returnsById() {
        final String id = createSchedule("Get test schedule");
        given().get("/workitem-schedules/" + id)
                .then().statusCode(200)
                .body("id", equalTo(id))
                .body("name", equalTo("Get test schedule"));
    }

    @Test
    void getSchedule_returns404_forUnknown() {
        given().get("/workitem-schedules/00000000-0000-0000-0000-000000000000")
                .then().statusCode(404);
    }

    // ── PUT /workitem-schedules/{id}/active ───────────────────────────────────

    @Test
    void setActive_false_disablesSchedule() {
        final String id = createSchedule("Toggle test");
        given().contentType(ContentType.JSON)
                .body("{\"active\":false}")
                .put("/workitem-schedules/" + id + "/active")
                .then().statusCode(200)
                .body("active", equalTo(false));
    }

    @Test
    void setActive_true_reEnablesSchedule_andRecomputesNextFireAt() {
        final String id = createSchedule("Re-enable test");
        given().contentType(ContentType.JSON).body("{\"active\":false}")
                .put("/workitem-schedules/" + id + "/active").then().statusCode(200);

        given().contentType(ContentType.JSON).body("{\"active\":true}")
                .put("/workitem-schedules/" + id + "/active")
                .then().statusCode(200)
                .body("active", equalTo(true))
                .body("nextFireAt", notNullValue());
    }

    // ── DELETE /workitem-schedules/{id} ───────────────────────────────────────

    @Test
    void deleteSchedule_returns204_andScheduleIsGone() {
        final String id = createSchedule("To delete");
        given().delete("/workitem-schedules/" + id).then().statusCode(204);
        given().get("/workitem-schedules/" + id).then().statusCode(404);
    }

    @Test
    void deleteSchedule_returns404_forUnknown() {
        given().delete("/workitem-schedules/00000000-0000-0000-0000-000000000000")
                .then().statusCode(404);
    }

    // ── E2E: processSchedules fires a due schedule ────────────────────────────

    @Test
    void e2e_processSchedules_createsWorkItem_whenScheduleIsDue() {
        final String templateId = createTemplate();
        final String scheduleId = given().contentType(ContentType.JSON)
                .body("{\"name\":\"Due schedule\",\"templateId\":\"" + templateId
                        + "\",\"cronExpression\":\"* * * * * ?\",\"createdBy\":\"admin\"}")
                .post("/workitem-schedules").then().statusCode(201).extract().path("id");

        // Force nextFireAt to be in the past so the schedule is immediately due
        scheduleService.forceDue(java.util.UUID.fromString(scheduleId));

        // Trigger the check manually
        final int created = scheduleService.processSchedules();

        assertThat(created).isGreaterThanOrEqualTo(1);

        // Verify a WorkItem was created with the template's category
        given().queryParam("category", "schedule-test-cat")
                .get("/workitems")
                .then().statusCode(200)
                .body("$", org.hamcrest.Matchers.hasSize(greaterThanOrEqualTo(1)));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String createTemplate() {
        return given().contentType(ContentType.JSON)
                .body("{\"name\":\"Schedule template\",\"category\":\"schedule-test-cat\",\"createdBy\":\"admin\"}")
                .post("/workitem-templates").then().statusCode(201).extract().path("id");
    }

    private String createSchedule(final String name) {
        return given().contentType(ContentType.JSON)
                .body("{\"name\":\"" + name + "\",\"templateId\":\"" + createTemplate()
                        + "\",\"cronExpression\":\"0 0 9 * * ?\",\"createdBy\":\"admin\"}")
                .post("/workitem-schedules").then().statusCode(201).extract().path("id");
    }
}
