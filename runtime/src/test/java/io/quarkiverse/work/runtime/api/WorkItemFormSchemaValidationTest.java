package io.quarkiverse.work.runtime.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * Integration and E2E tests for payload/resolution validation against WorkItemFormSchema.
 *
 * <p>
 * When a WorkItemFormSchema exists for a category:
 * - POST /workitems validates payload against payloadSchema → 400 on violation
 * - PUT /workitems/{id}/complete validates resolution against resolutionSchema → 400 on violation
 * - No schema → no validation (backward compatible)
 * - Null/absent payload or resolution → skip validation (schema is optional data)
 *
 * <p>
 * Issue #108, Epic #98.
 */
@QuarkusTest
class WorkItemFormSchemaValidationTest {

    private static final String LOAN_PAYLOAD_SCHEMA = """
            {"type":"object","properties":{"loanAmount":{"type":"number","minimum":1},"purpose":{"type":"string"}},"required":["loanAmount"]}
            """
            .strip();

    private static final String APPROVAL_RESOLUTION_SCHEMA = """
            {"type":"object","properties":{"approved":{"type":"boolean"},"reason":{"type":"string"}},"required":["approved"]}
            """.strip();

    // ── POST /workitems — payload validation ──────────────────────────────────

    @Test
    void createWorkItem_returns201_whenPayloadSatisfiesSchema() {
        final String category = "loan-" + System.nanoTime();
        createFormSchema(category, LOAN_PAYLOAD_SCHEMA, null);

        given().contentType(ContentType.JSON)
                .body("{\"title\":\"Loan Review\",\"category\":\"" + category + "\"," +
                        "\"payload\":\"{\\\"loanAmount\\\":5000,\\\"purpose\\\":\\\"home\\\"}\"," +
                        "\"createdBy\":\"system\"}")
                .post("/workitems")
                .then()
                .statusCode(201)
                .body("id", notNullValue());
    }

    @Test
    void createWorkItem_returns400_whenPayloadViolatesSchema() {
        final String category = "loan-bad-" + System.nanoTime();
        createFormSchema(category, LOAN_PAYLOAD_SCHEMA, null);

        given().contentType(ContentType.JSON)
                .body("{\"title\":\"Bad Loan\",\"category\":\"" + category + "\"," +
                        "\"payload\":\"{\\\"purpose\\\":\\\"home\\\"}\"," + // missing required loanAmount
                        "\"createdBy\":\"system\"}")
                .post("/workitems")
                .then()
                .statusCode(400)
                .body("error", containsStringIgnoringCase("payload"))
                .body("violations", not(empty()));
    }

    @Test
    void createWorkItem_returns400_withFieldLevelErrors_forWrongType() {
        final String category = "loan-type-" + System.nanoTime();
        createFormSchema(category, LOAN_PAYLOAD_SCHEMA, null);

        given().contentType(ContentType.JSON)
                .body("{\"title\":\"Wrong Type\",\"category\":\"" + category + "\"," +
                        "\"payload\":\"{\\\"loanAmount\\\":\\\"not-a-number\\\"}\"," +
                        "\"createdBy\":\"system\"}")
                .post("/workitems")
                .then()
                .statusCode(400)
                .body("violations", not(empty()));
    }

    @Test
    void createWorkItem_returns201_whenNoSchemaForCategory() {
        // Backward compatibility — no schema = no validation
        final String category = "no-schema-" + System.nanoTime();

        given().contentType(ContentType.JSON)
                .body("{\"title\":\"Free Form\",\"category\":\"" + category + "\"," +
                        "\"payload\":\"{\\\"anything\\\":\\\"goes\\\"}\"," +
                        "\"createdBy\":\"system\"}")
                .post("/workitems")
                .then()
                .statusCode(201);
    }

    @Test
    void createWorkItem_returns201_whenNullPayload_andSchemaExists() {
        // Null payload → skip validation (payload is optional on WorkItem)
        final String category = "loan-null-payload-" + System.nanoTime();
        createFormSchema(category, LOAN_PAYLOAD_SCHEMA, null);

        given().contentType(ContentType.JSON)
                .body("{\"title\":\"No Payload Loan\",\"category\":\"" + category + "\"," +
                        "\"createdBy\":\"system\"}")
                .post("/workitems")
                .then()
                .statusCode(201);
    }

    @Test
    void createWorkItem_returns201_whenSchemaHasNoPayloadSchema() {
        // Schema exists for category but payloadSchema is null — no payload validation
        final String category = "no-payload-schema-" + System.nanoTime();
        createFormSchema(category, null, APPROVAL_RESOLUTION_SCHEMA);

        given().contentType(ContentType.JSON)
                .body("{\"title\":\"Any Payload\",\"category\":\"" + category + "\"," +
                        "\"payload\":\"{\\\"anything\\\":\\\"goes\\\"}\"," +
                        "\"createdBy\":\"system\"}")
                .post("/workitems")
                .then()
                .statusCode(201);
    }

    @Test
    void createWorkItem_returns201_whenCategoryIsNull_noValidation() {
        // WorkItem with no category — no schema lookup, no validation
        given().contentType(ContentType.JSON)
                .body("{\"title\":\"Uncategorised\",\"payload\":\"{\\\"x\\\":1}\",\"createdBy\":\"system\"}")
                .post("/workitems")
                .then()
                .statusCode(201);
    }

    // ── PUT /workitems/{id}/complete — resolution validation ──────────────────

    @Test
    void completeWorkItem_returns200_whenResolutionSatisfiesSchema() {
        final String category = "approval-" + System.nanoTime();
        createFormSchema(category, null, APPROVAL_RESOLUTION_SCHEMA);

        final String id = createAndStartWorkItem(category);

        given().contentType(ContentType.JSON)
                .body("{\"resolution\":\"{\\\"approved\\\":true,\\\"reason\\\":\\\"All clear\\\"}\"}")
                .put("/workitems/" + id + "/complete?actor=reviewer")
                .then()
                .statusCode(200);
    }

    @Test
    void completeWorkItem_returns400_whenResolutionViolatesSchema() {
        final String category = "approval-bad-" + System.nanoTime();
        createFormSchema(category, null, APPROVAL_RESOLUTION_SCHEMA);

        final String id = createAndStartWorkItem(category);

        given().contentType(ContentType.JSON)
                .body("{\"resolution\":\"{\\\"reason\\\":\\\"no decision field\\\"}\"}") // missing required 'approved'
                .put("/workitems/" + id + "/complete?actor=reviewer")
                .then()
                .statusCode(400)
                .body("error", containsStringIgnoringCase("resolution"))
                .body("violations", not(empty()));
    }

    @Test
    void completeWorkItem_returns200_whenNoSchemaForCategory() {
        // Backward compatibility — no schema = no validation on complete
        final String category = "free-complete-" + System.nanoTime();
        final String id = createAndStartWorkItem(category);

        given().contentType(ContentType.JSON)
                .body("{\"resolution\":\"{\\\"anything\\\":\\\"goes\\\"}\"}")
                .put("/workitems/" + id + "/complete?actor=reviewer")
                .then()
                .statusCode(200);
    }

    @Test
    void completeWorkItem_returns200_whenNullResolution_andSchemaExists() {
        // Null resolution → skip validation
        final String category = "approval-null-res-" + System.nanoTime();
        createFormSchema(category, null, APPROVAL_RESOLUTION_SCHEMA);

        final String id = createAndStartWorkItem(category);

        given().contentType(ContentType.JSON)
                .body("{}")
                .put("/workitems/" + id + "/complete?actor=reviewer")
                .then()
                .statusCode(200);
    }

    @Test
    void completeWorkItem_returns400_wrongTypeInResolution() {
        final String category = "approval-type-" + System.nanoTime();
        createFormSchema(category, null, APPROVAL_RESOLUTION_SCHEMA);

        final String id = createAndStartWorkItem(category);

        given().contentType(ContentType.JSON)
                .body("{\"resolution\":\"{\\\"approved\\\":\\\"yes-as-string\\\"}\"}") // boolean as string
                .put("/workitems/" + id + "/complete?actor=reviewer")
                .then()
                .statusCode(400)
                .body("violations", not(empty()));
    }

    // ── Latest schema wins when multiple schemas exist for category ───────────

    @Test
    void createWorkItem_usesLatestSchemaByCreatedAt() {
        final String category = "multi-schema-" + System.nanoTime();

        // Create a permissive schema first (no required fields)
        createFormSchemaWithVersion(category, "{\"type\":\"object\"}", null, "v1");

        // Then a strict schema requiring 'loanAmount'
        createFormSchemaWithVersion(category, LOAN_PAYLOAD_SCHEMA, null, "v2");

        // Payload violates v2 (strict) — should be rejected because v2 is latest
        given().contentType(ContentType.JSON)
                .body("{\"title\":\"Multi Schema\",\"category\":\"" + category + "\"," +
                        "\"payload\":\"{\\\"purpose\\\":\\\"only-purpose-no-amount\\\"}\"," +
                        "\"createdBy\":\"system\"}")
                .post("/workitems")
                .then()
                .statusCode(400)
                .body("violations", not(empty()));
    }

    // ── E2E: full lifecycle with schema validation ────────────────────────────

    @Test
    void e2e_createWithValidPayload_completeWithValidResolution() {
        final String category = "e2e-schema-" + System.nanoTime();
        createFormSchema(category, LOAN_PAYLOAD_SCHEMA, APPROVAL_RESOLUTION_SCHEMA);

        // Create WorkItem with valid payload
        final String id = given().contentType(ContentType.JSON)
                .body("{\"title\":\"Loan Approval E2E\",\"category\":\"" + category + "\"," +
                        "\"payload\":\"{\\\"loanAmount\\\":10000,\\\"purpose\\\":\\\"business\\\"}\"," +
                        "\"createdBy\":\"system\"}")
                .post("/workitems")
                .then()
                .statusCode(201)
                .extract().path("id");

        // Advance to IN_PROGRESS
        given().put("/workitems/" + id + "/claim?claimant=reviewer").then().statusCode(200);
        given().put("/workitems/" + id + "/start?actor=reviewer").then().statusCode(200);

        // Complete with valid resolution
        given().contentType(ContentType.JSON)
                .body("{\"resolution\":\"{\\\"approved\\\":true,\\\"reason\\\":\\\"Strong application\\\"}\"}")
                .put("/workitems/" + id + "/complete?actor=reviewer")
                .then()
                .statusCode(200)
                .body("status", equalTo("COMPLETED"));
    }

    @Test
    void e2e_createFails_whenPayloadInvalid_thenSucceedsWithValidPayload() {
        final String category = "e2e-retry-" + System.nanoTime();
        createFormSchema(category, LOAN_PAYLOAD_SCHEMA, null);

        // First attempt: invalid payload
        given().contentType(ContentType.JSON)
                .body("{\"title\":\"Retry Test\",\"category\":\"" + category + "\"," +
                        "\"payload\":\"{\\\"purpose\\\":\\\"missing-amount\\\"}\"," +
                        "\"createdBy\":\"system\"}")
                .post("/workitems")
                .then()
                .statusCode(400)
                .body("violations", not(empty()));

        // Second attempt: correct payload
        given().contentType(ContentType.JSON)
                .body("{\"title\":\"Retry Test\",\"category\":\"" + category + "\"," +
                        "\"payload\":\"{\\\"loanAmount\\\":2500,\\\"purpose\\\":\\\"fixed\\\"}\"," +
                        "\"createdBy\":\"system\"}")
                .post("/workitems")
                .then()
                .statusCode(201)
                .body("id", notNullValue());
    }

    @Test
    void e2e_completeFails_whenResolutionInvalid_thenSucceedsWithValidResolution() {
        final String category = "e2e-res-retry-" + System.nanoTime();
        createFormSchema(category, null, APPROVAL_RESOLUTION_SCHEMA);

        final String id = createAndStartWorkItem(category);

        // First attempt: invalid resolution
        given().contentType(ContentType.JSON)
                .body("{\"resolution\":\"{\\\"reason\\\":\\\"forgot-approved\\\"}\"}") // missing required 'approved'
                .put("/workitems/" + id + "/complete?actor=reviewer")
                .then()
                .statusCode(400);

        // Second attempt: valid resolution — WorkItem should still be IN_PROGRESS (not corrupted)
        given().contentType(ContentType.JSON)
                .body("{\"resolution\":\"{\\\"approved\\\":false,\\\"reason\\\":\\\"Insufficient collateral\\\"}\"}")
                .put("/workitems/" + id + "/complete?actor=reviewer")
                .then()
                .statusCode(200)
                .body("status", equalTo("COMPLETED"));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void createFormSchema(final String category, final String payloadSchema,
            final String resolutionSchema) {
        createFormSchemaWithVersion(category, payloadSchema, resolutionSchema, "1.0");
    }

    private void createFormSchemaWithVersion(final String category, final String payloadSchema,
            final String resolutionSchema, final String version) {
        final StringBuilder body = new StringBuilder("{\"name\":\"Schema-").append(category)
                .append("\",\"category\":\"").append(category)
                .append("\",\"schemaVersion\":\"").append(version)
                .append("\",\"createdBy\":\"test\"");
        if (payloadSchema != null)
            body.append(",\"payloadSchema\":\"").append(payloadSchema.replace("\"", "\\\"")).append("\"");
        if (resolutionSchema != null)
            body.append(",\"resolutionSchema\":\"").append(resolutionSchema.replace("\"", "\\\"")).append("\"");
        body.append("}");

        given().contentType(ContentType.JSON)
                .body(body.toString())
                .post("/workitem-form-schemas")
                .then().statusCode(201);
    }

    /** Creates a WorkItem, claims it, and starts it (puts it in IN_PROGRESS). */
    private String createAndStartWorkItem(final String category) {
        final String id = given().contentType(ContentType.JSON)
                .body("{\"title\":\"Validation Test\",\"category\":\"" + category + "\",\"createdBy\":\"system\"}")
                .post("/workitems")
                .then().statusCode(201).extract().path("id");

        given().put("/workitems/" + id + "/claim?claimant=reviewer").then().statusCode(200);
        given().put("/workitems/" + id + "/start?actor=reviewer").then().statusCode(200);
        return id;
    }
}
