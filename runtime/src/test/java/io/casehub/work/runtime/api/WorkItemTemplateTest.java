package io.casehub.work.runtime.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * Integration and end-to-end tests for WorkItemTemplate.
 *
 * <h2>What templates are for</h2>
 * <p>
 * A template pre-defines the category, priority, candidate groups, expiry defaults,
 * payload schema, and labels for a repeatable process. Instantiating one creates a
 * fully-configured WorkItem in a single call — no need to repeat the same 15-field
 * body every time a loan application arrives, a security incident is reported, etc.
 *
 * <h2>Test tiers</h2>
 * <ul>
 * <li><strong>Unit</strong> — template-to-WorkItem mapping (WorkItemTemplateServiceTest)</li>
 * <li><strong>Integration</strong> — CRUD for templates via REST</li>
 * <li><strong>Happy path</strong> — create template, instantiate it, verify WorkItem fields</li>
 * <li><strong>E2E</strong> — override defaults at instantiation time</li>
 * </ul>
 */
@QuarkusTest
class WorkItemTemplateTest {

    // ── POST /workitem-templates ──────────────────────────────────────────────

    @Test
    void createTemplate_returns201_withId() {
        given().contentType(ContentType.JSON)
                .body("""
                        {"name":"Loan Approval","category":"finance","priority":"HIGH",
                         "candidateGroups":"loan-officers","defaultExpiryHours":48,
                         "createdBy":"admin"}
                        """)
                .post("/workitem-templates")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("name", equalTo("Loan Approval"))
                .body("category", equalTo("finance"))
                .body("priority", equalTo("HIGH"))
                .body("candidateGroups", equalTo("loan-officers"))
                .body("defaultExpiryHours", equalTo(48))
                .body("createdBy", equalTo("admin"));
    }

    @Test
    void createTemplate_returns400_whenNameMissing() {
        given().contentType(ContentType.JSON)
                .body("{\"category\":\"finance\",\"createdBy\":\"admin\"}")
                .post("/workitem-templates")
                .then()
                .statusCode(400);
    }

    @Test
    void createTemplate_returns400_whenCreatedByMissing() {
        given().contentType(ContentType.JSON)
                .body("{\"name\":\"Test\",\"category\":\"ops\"}")
                .post("/workitem-templates")
                .then()
                .statusCode(400);
    }

    @Test
    void createTemplate_withMinimalFields_succeeds() {
        given().contentType(ContentType.JSON)
                .body("{\"name\":\"Minimal Template\",\"createdBy\":\"alice\"}")
                .post("/workitem-templates")
                .then()
                .statusCode(201)
                .body("category", nullValue())
                .body("priority", nullValue())
                .body("defaultExpiryHours", nullValue());
    }

    // ── GET /workitem-templates ───────────────────────────────────────────────

    @Test
    void listTemplates_includesCreatedTemplate() {
        given().contentType(ContentType.JSON)
                .body("{\"name\":\"Security Triage Template\",\"category\":\"security\",\"createdBy\":\"admin\"}")
                .post("/workitem-templates")
                .then().statusCode(201);

        given().get("/workitem-templates")
                .then()
                .statusCode(200)
                .body("name", hasItem("Security Triage Template"));
    }

    // ── GET /workitem-templates/{id} ──────────────────────────────────────────

    @Test
    void getTemplate_returnsById() {
        final String id = given().contentType(ContentType.JSON)
                .body("{\"name\":\"Compliance Review\",\"category\":\"legal\",\"createdBy\":\"admin\"}")
                .post("/workitem-templates")
                .then().statusCode(201)
                .extract().path("id");

        given().get("/workitem-templates/" + id)
                .then()
                .statusCode(200)
                .body("id", equalTo(id))
                .body("name", equalTo("Compliance Review"))
                .body("category", equalTo("legal"));
    }

    @Test
    void getTemplate_returns404_forUnknownId() {
        given().get("/workitem-templates/00000000-0000-0000-0000-000000000000")
                .then()
                .statusCode(404);
    }

    // ── DELETE /workitem-templates/{id} ───────────────────────────────────────

    @Test
    void deleteTemplate_returns204_andTemplateIsGone() {
        final String id = given().contentType(ContentType.JSON)
                .body("{\"name\":\"To Delete\",\"createdBy\":\"alice\"}")
                .post("/workitem-templates")
                .then().statusCode(201)
                .extract().path("id");

        given().delete("/workitem-templates/" + id).then().statusCode(204);
        given().get("/workitem-templates/" + id).then().statusCode(404);
    }

    @Test
    void deleteTemplate_returns404_forUnknownId() {
        given().delete("/workitem-templates/00000000-0000-0000-0000-000000000000")
                .then().statusCode(404);
    }

    // ── Happy path: instantiate template ─────────────────────────────────────

    @Test
    void instantiate_createsWorkItemWithTemplateDefaults() {
        final String templateId = given().contentType(ContentType.JSON)
                .body("""
                        {"name":"NDA Review","category":"legal","priority":"HIGH",
                         "candidateGroups":"legal-team","defaultExpiryHours":72,
                         "defaultPayload":"{\\\"type\\\":\\\"nda\\\"}",
                         "createdBy":"admin"}
                        """)
                .post("/workitem-templates")
                .then().statusCode(201)
                .extract().path("id");

        given().contentType(ContentType.JSON)
                .body("{\"createdBy\":\"system\"}")
                .post("/workitem-templates/" + templateId + "/instantiate")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("category", equalTo("legal"))
                .body("priority", equalTo("HIGH"))
                .body("candidateGroups", equalTo("legal-team"))
                .body("payload", equalTo("{\"type\":\"nda\"}"))
                .body("status", equalTo("PENDING"));
    }

    @Test
    void instantiate_titleDefaultsToTemplateName() {
        final String templateId = given().contentType(ContentType.JSON)
                .body("{\"name\":\"Standard Security Review\",\"createdBy\":\"admin\"}")
                .post("/workitem-templates")
                .then().statusCode(201)
                .extract().path("id");

        given().contentType(ContentType.JSON)
                .body("{\"createdBy\":\"agent-1\"}")
                .post("/workitem-templates/" + templateId + "/instantiate")
                .then()
                .statusCode(201)
                .body("title", equalTo("Standard Security Review"));
    }

    // ── E2E: override defaults at instantiation time ──────────────────────────

    @Test
    void instantiate_withTitleOverride_usesProvidedTitle() {
        final String templateId = given().contentType(ContentType.JSON)
                .body("{\"name\":\"Finance Review\",\"category\":\"finance\",\"createdBy\":\"admin\"}")
                .post("/workitem-templates")
                .then().statusCode(201)
                .extract().path("id");

        given().contentType(ContentType.JSON)
                .body("{\"title\":\"Q4 budget reallocation — £50k\",\"createdBy\":\"finance-bot\"}")
                .post("/workitem-templates/" + templateId + "/instantiate")
                .then()
                .statusCode(201)
                .body("title", equalTo("Q4 budget reallocation — £50k"))
                .body("category", equalTo("finance")); // still from template
    }

    @Test
    void instantiate_withAssigneeOverride_assignsDirectly() {
        final String templateId = given().contentType(ContentType.JSON)
                .body("{\"name\":\"Direct Assignment\",\"createdBy\":\"admin\"}")
                .post("/workitem-templates")
                .then().statusCode(201)
                .extract().path("id");

        given().contentType(ContentType.JSON)
                .body("{\"assigneeId\":\"alice\",\"createdBy\":\"system\"}")
                .post("/workitem-templates/" + templateId + "/instantiate")
                .then()
                .statusCode(201)
                .body("assigneeId", equalTo("alice"));
    }

    @Test
    void instantiate_returns404_forUnknownTemplate() {
        given().contentType(ContentType.JSON)
                .body("{\"createdBy\":\"system\"}")
                .post("/workitem-templates/00000000-0000-0000-0000-000000000000/instantiate")
                .then()
                .statusCode(404);
    }

    @Test
    void instantiate_preservesTemplateLabels() {
        final String templateId = given().contentType(ContentType.JSON)
                .body("""
                        {"name":"Labelled Template","category":"ops",
                         "labelPaths":"[\\"intake/triage\\",\\"priority/high\\"]",
                         "createdBy":"admin"}
                        """)
                .post("/workitem-templates")
                .then().statusCode(201)
                .extract().path("id");

        given().contentType(ContentType.JSON)
                .body("{\"createdBy\":\"system\"}")
                .post("/workitem-templates/" + templateId + "/instantiate")
                .then()
                .statusCode(201)
                .body("labels.path", hasItem("intake/triage"))
                .body("labels.path", hasItem("priority/high"))
                .body("labels.findAll { it.persistence == 'MANUAL' }", hasSize(2));
    }
}
