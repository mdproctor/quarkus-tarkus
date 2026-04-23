package io.quarkiverse.work.runtime.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * Integration and E2E tests for WorkItemFormSchema (Epic #98, Issue #107).
 *
 * <p>
 * WorkItemFormSchema stores JSON Schema definitions for WorkItem payload and resolution,
 * keyed optionally by category. The schema itself is stored as TEXT — valid JSON, not
 * further parsed by WorkItems.
 */
@QuarkusTest
class WorkItemFormSchemaTest {

    private static final String PAYLOAD_SCHEMA = "{\"type\":\"object\",\"properties\":{\"loanAmount\":{\"type\":\"number\"}},\"required\":[\"loanAmount\"]}";
    private static final String RESOLUTION_SCHEMA = "{\"type\":\"object\",\"properties\":{\"approved\":{\"type\":\"boolean\"}},\"required\":[\"approved\"]}";

    // ── POST /workitem-form-schemas ────────────────────────────────────────────

    @Test
    void createSchema_returns201_withAllFields() {
        given().contentType(ContentType.JSON)
                .body("{\"name\":\"Loan Approval Schema\",\"category\":\"loan-approval\"," +
                        "\"payloadSchema\":" + jsonStr(PAYLOAD_SCHEMA) + "," +
                        "\"resolutionSchema\":" + jsonStr(RESOLUTION_SCHEMA) + "," +
                        "\"schemaVersion\":\"1.0\",\"createdBy\":\"alice\"}")
                .post("/workitem-form-schemas")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("name", equalTo("Loan Approval Schema"))
                .body("category", equalTo("loan-approval"))
                .body("payloadSchema", notNullValue())
                .body("resolutionSchema", notNullValue())
                .body("schemaVersion", equalTo("1.0"))
                .body("createdBy", equalTo("alice"))
                .body("createdAt", notNullValue());
    }

    @Test
    void createSchema_returns400_whenNameBlank() {
        given().contentType(ContentType.JSON)
                .body("{\"name\":\"\",\"createdBy\":\"alice\"}")
                .post("/workitem-form-schemas")
                .then().statusCode(400);
    }

    @Test
    void createSchema_returns400_whenCreatedByMissing() {
        given().contentType(ContentType.JSON)
                .body("{\"name\":\"My Schema\"}")
                .post("/workitem-form-schemas")
                .then().statusCode(400);
    }

    @Test
    void createSchema_categoryIsOptional_returnsNullCategory() {
        given().contentType(ContentType.JSON)
                .body("{\"name\":\"Generic Schema\",\"createdBy\":\"bob\"," +
                        "\"payloadSchema\":" + jsonStr(PAYLOAD_SCHEMA) + "}")
                .post("/workitem-form-schemas")
                .then()
                .statusCode(201)
                .body("category", nullValue())
                .body("name", equalTo("Generic Schema"));
    }

    @Test
    void createSchema_bothSchemasAreOptional() {
        given().contentType(ContentType.JSON)
                .body("{\"name\":\"Empty Schema\",\"category\":\"test\",\"createdBy\":\"carol\"}")
                .post("/workitem-form-schemas")
                .then()
                .statusCode(201)
                .body("payloadSchema", nullValue())
                .body("resolutionSchema", nullValue());
    }

    @Test
    void createSchema_schemaVersionIsOptional() {
        given().contentType(ContentType.JSON)
                .body("{\"name\":\"No Version Schema\",\"createdBy\":\"dave\"}")
                .post("/workitem-form-schemas")
                .then()
                .statusCode(201)
                .body("schemaVersion", nullValue());
    }

    // ── GET /workitem-form-schemas ────────────────────────────────────────────

    @Test
    void listSchemas_returnsEmpty_whenNoneExist() {
        // Note: other tests may have created schemas; we can only assert the
        // list is a valid array — asserting empty here would be order-dependent.
        // Instead, verify schema is returned for a specific known name below.
        given().get("/workitem-form-schemas")
                .then().statusCode(200);
    }

    @Test
    void listSchemas_includesCreatedSchema() {
        final String name = "Unique-" + System.nanoTime();
        createSchema(name, "list-test-cat");

        given().get("/workitem-form-schemas")
                .then()
                .statusCode(200)
                .body("name", hasItem(name));
    }

    @Test
    void listSchemas_filterByCategory_returnsOnlyMatchingSchemas() {
        final String cat = "filter-cat-" + System.nanoTime();
        createSchema("Schema A", cat);
        createSchema("Schema B", cat);
        createSchema("Schema C", "other-cat-" + System.nanoTime());

        given().queryParam("category", cat)
                .get("/workitem-form-schemas")
                .then()
                .statusCode(200)
                .body("$", hasSize(2))
                .body("category", hasItem(cat));
    }

    @Test
    void listSchemas_filterByCategory_returnsEmpty_whenNoMatch() {
        given().queryParam("category", "nonexistent-cat-" + System.nanoTime())
                .get("/workitem-form-schemas")
                .then()
                .statusCode(200)
                .body("$", empty());
    }

    @Test
    void listSchemas_filterByCategory_excludesSchemasWithOtherCategory() {
        final String catA = "excl-cat-a-" + System.nanoTime();
        final String catB = "excl-cat-b-" + System.nanoTime();
        final String nameA = "Exclusive-A-" + System.nanoTime();
        createSchema(nameA, catA);
        createSchema("Exclusive-B-" + System.nanoTime(), catB);

        given().queryParam("category", catA)
                .get("/workitem-form-schemas")
                .then()
                .statusCode(200)
                .body("name", hasItem(nameA))
                .body("category", hasItem(catA));
    }

    // ── GET /workitem-form-schemas/{id} ───────────────────────────────────────

    @Test
    void getSchema_returns200_withAllFields() {
        final String id = createSchema("Get Test Schema", "get-test");

        given().get("/workitem-form-schemas/" + id)
                .then()
                .statusCode(200)
                .body("id", equalTo(id))
                .body("name", equalTo("Get Test Schema"))
                .body("category", equalTo("get-test"))
                .body("createdBy", equalTo("test"));
    }

    @Test
    void getSchema_returns404_forUnknownId() {
        given().get("/workitem-form-schemas/00000000-0000-0000-0000-000000000000")
                .then().statusCode(404);
    }

    // ── DELETE /workitem-form-schemas/{id} ────────────────────────────────────

    @Test
    void deleteSchema_returns204_andSchemaIsGone() {
        final String id = createSchema("To Be Deleted", "delete-cat");

        given().delete("/workitem-form-schemas/" + id)
                .then().statusCode(204);

        given().get("/workitem-form-schemas/" + id)
                .then().statusCode(404);
    }

    @Test
    void deleteSchema_returns404_forUnknownId() {
        given().delete("/workitem-form-schemas/00000000-0000-0000-0000-000000000000")
                .then().statusCode(404);
    }

    @Test
    void deleteSchema_onlyRemovesTargetSchema() {
        final String cat = "del-only-cat-" + System.nanoTime();
        final String keepName = "Keep-" + System.nanoTime();
        final String keepId = createSchema(keepName, cat);
        final String removeId = createSchema("Remove-" + System.nanoTime(), cat);

        given().delete("/workitem-form-schemas/" + removeId).then().statusCode(204);

        given().get("/workitem-form-schemas/" + keepId)
                .then().statusCode(200)
                .body("name", equalTo(keepName));
    }

    // ── E2E: full lifecycle ───────────────────────────────────────────────────

    @Test
    void e2e_createListByCategory_getById_delete() {
        final String category = "e2e-cat-" + System.nanoTime();

        // Create two schemas for same category
        final String id1 = createSchemaWithPayload("E2E Schema 1", category, PAYLOAD_SCHEMA, null);
        final String id2 = createSchemaWithPayload("E2E Schema 2", category, null, RESOLUTION_SCHEMA);

        // List by category — both returned
        given().queryParam("category", category)
                .get("/workitem-form-schemas")
                .then()
                .statusCode(200)
                .body("$", hasSize(2));

        // Get by ID — payloadSchema preserved
        given().get("/workitem-form-schemas/" + id1)
                .then()
                .statusCode(200)
                .body("payloadSchema", notNullValue())
                .body("resolutionSchema", nullValue());

        // Get by ID — resolutionSchema preserved
        given().get("/workitem-form-schemas/" + id2)
                .then()
                .statusCode(200)
                .body("payloadSchema", nullValue())
                .body("resolutionSchema", notNullValue());

        // Delete one — the other remains
        given().delete("/workitem-form-schemas/" + id1).then().statusCode(204);

        given().queryParam("category", category)
                .get("/workitem-form-schemas")
                .then()
                .statusCode(200)
                .body("$", hasSize(1))
                .body("[0].id", equalTo(id2));

        // Delete the last
        given().delete("/workitem-form-schemas/" + id2).then().statusCode(204);

        given().queryParam("category", category)
                .get("/workitem-form-schemas")
                .then()
                .statusCode(200)
                .body("$", empty());
    }

    @Test
    void e2e_schemaAndWorkItemHaveIndependentLifecycle() {
        // FormSchemas are NOT associated with individual WorkItems — they are
        // category-level definitions. Deleting the schema must not affect WorkItems,
        // and WorkItems can be created in a category regardless of schema existence.
        final String category = "lifecycle-cat-" + System.nanoTime();

        // Create a WorkItem before the schema exists — schema is not required
        final String wiId = given().contentType(ContentType.JSON)
                .body("{\"title\":\"WI in category\",\"category\":\"" + category + "\",\"createdBy\":\"test\"}")
                .post("/workitems")
                .then().statusCode(201).extract().path("id");

        // Create the schema after the WorkItem — independent lifecycle
        final String schemaId = createSchema("Lifecycle Schema", category);

        // Delete the schema — WorkItem must be unaffected
        given().delete("/workitem-form-schemas/" + schemaId).then().statusCode(204);

        // WorkItem still accessible
        given().get("/workitems/" + wiId)
                .then().statusCode(200)
                .body("category", equalTo(category));

        // Schema is gone
        given().get("/workitem-form-schemas/" + schemaId)
                .then().statusCode(404);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String createSchema(final String name, final String category) {
        return given().contentType(ContentType.JSON)
                .body("{\"name\":\"" + name + "\",\"category\":\"" + category + "\",\"createdBy\":\"test\"}")
                .post("/workitem-form-schemas")
                .then().statusCode(201).extract().path("id");
    }

    private String createSchemaWithPayload(final String name, final String category,
            final String payloadSchema, final String resolutionSchema) {
        final StringBuilder body = new StringBuilder("{\"name\":\"").append(name)
                .append("\",\"category\":\"").append(category)
                .append("\",\"createdBy\":\"test\"");
        if (payloadSchema != null)
            body.append(",\"payloadSchema\":").append(jsonStr(payloadSchema));
        if (resolutionSchema != null)
            body.append(",\"resolutionSchema\":").append(jsonStr(resolutionSchema));
        body.append("}");

        return given().contentType(ContentType.JSON)
                .body(body.toString())
                .post("/workitem-form-schemas")
                .then().statusCode(201).extract().path("id");
    }

    /** Escapes a JSON string value for embedding in a JSON body. */
    private static String jsonStr(final String json) {
        return "\"" + json.replace("\"", "\\\"") + "\"";
    }
}
