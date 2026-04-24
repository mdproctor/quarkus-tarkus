package io.quarkiverse.work.runtime.spawn;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;

@QuarkusTest
class SpawnCorrectnessTest {

    @Test
    void partOf_direction_isChildToParent() {
        final String tmplId = createTemplate("dir-tmpl");
        final String parentId = createWorkItem("dir-parent");

        final Response spawnResp = given()
                .contentType(ContentType.JSON)
                .body(Map.of("idempotencyKey", "dir-" + UUID.randomUUID(),
                        "children", List.of(Map.of("templateId", tmplId))))
                .when().post("/workitems/" + parentId + "/spawn")
                .then().statusCode(201).extract().response();

        final String childId = spawnResp.jsonPath().getString("children[0].workItemId");

        // Child has outgoing PART_OF relation pointing to parent
        final List<Map<String, Object>> outgoing = given()
                .when().get("/workitems/" + childId + "/relations")
                .then().statusCode(200).extract().jsonPath().getList("$");

        assertThat(outgoing).anySatisfy(rel -> {
            assertThat(rel.get("relationType")).isEqualTo("PART_OF");
            assertThat(rel.get("targetId")).isEqualTo(parentId);
        });
    }

    @Test
    void callerRef_null_storedAsNull() {
        final String tmplId = createTemplate("null-ref-tmpl");
        final String parentId = createWorkItem("null-ref-parent");

        final Response spawnResp = given()
                .contentType(ContentType.JSON)
                .body(Map.of("idempotencyKey", "null-ref-" + UUID.randomUUID(),
                        "children", List.of(Map.of("templateId", tmplId))))
                .when().post("/workitems/" + parentId + "/spawn")
                .then().statusCode(201).extract().response();

        final String childId = spawnResp.jsonPath().getString("children[0].workItemId");

        // callerRef not provided — stored as null (not empty string)
        final Object storedRef = given()
                .when().get("/workitems/" + childId)
                .then().statusCode(200).extract().path("callerRef");
        assertThat(storedRef).isNull();
    }

    @Test
    void overrides_candidateGroups_appliedOverTemplate() {
        final String tmplId = createTemplate("override-tmpl");
        final String parentId = createWorkItem("override-parent");

        final Response spawnResp = given()
                .contentType(ContentType.JSON)
                .body(Map.of("idempotencyKey", "override-" + UUID.randomUUID(),
                        "children", List.of(Map.of(
                                "templateId", tmplId,
                                "overrides", Map.of("candidateGroups", "fraud-team")))))
                .when().post("/workitems/" + parentId + "/spawn")
                .then().statusCode(201).extract().response();

        final String childId = spawnResp.jsonPath().getString("children[0].workItemId");

        // Overridden candidateGroups on child
        final String candidateGroups = given()
                .when().get("/workitems/" + childId)
                .then().statusCode(200).extract().path("candidateGroups");
        assertThat(candidateGroups).isEqualTo("fraud-team");
    }

    @Test
    void cycleGuard_preventsChildBecomingAncestorOfItself() {
        final String tmplId = createTemplate("cycle-tmpl");
        final String parentId = createWorkItem("cycle-parent");

        final Response spawnResp = given()
                .contentType(ContentType.JSON)
                .body(Map.of("idempotencyKey", "cycle-" + UUID.randomUUID(),
                        "children", List.of(Map.of("templateId", tmplId))))
                .when().post("/workitems/" + parentId + "/spawn")
                .then().statusCode(201).extract().response();

        final String childId = spawnResp.jsonPath().getString("children[0].workItemId");

        // Make parent PART_OF child — would create cycle — must be rejected
        given()
                .contentType(ContentType.JSON)
                .body(Map.of("targetId", childId, "relationType", "PART_OF", "createdBy", "test"))
                .when().post("/workitems/" + parentId + "/relations")
                .then().statusCode(400);
    }

    private String createTemplate(final String name) {
        return given().contentType(ContentType.JSON)
                .body(Map.of("name", name, "category", name, "createdBy", "test"))
                .when().post("/workitem-templates")
                .then().statusCode(201).extract().path("id");
    }

    private String createWorkItem(final String category) {
        return given().contentType(ContentType.JSON)
                .body(Map.of("title", "p-" + category, "category", category, "createdBy", "test"))
                .when().post("/workitems")
                .then().statusCode(201).extract().path("id");
    }
}
