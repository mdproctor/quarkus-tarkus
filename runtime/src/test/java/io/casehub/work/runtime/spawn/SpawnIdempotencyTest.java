package io.casehub.work.runtime.spawn;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

@QuarkusTest
class SpawnIdempotencyTest {

    @Test
    void sameIdempotencyKey_sameParent_returnsExistingGroup() {
        final String tmplId = given()
                .contentType(ContentType.JSON)
                .body(Map.of("name", "idem-tmpl", "category", "test", "createdBy", "test"))
                .when().post("/workitem-templates")
                .then().statusCode(201).extract().path("id");

        final String parentId = given()
                .contentType(ContentType.JSON)
                .body(Map.of("title", "parent", "category", "test", "createdBy", "test"))
                .when().post("/workitems")
                .then().statusCode(201).extract().path("id");

        final String key = "idem-key-" + UUID.randomUUID();
        final var body = Map.of("idempotencyKey", key,
                "children", List.of(Map.of("templateId", tmplId)));

        // First call — 201
        final String groupId1 = given()
                .contentType(ContentType.JSON).body(body)
                .when().post("/workitems/" + parentId + "/spawn")
                .then().statusCode(201).extract().path("groupId");

        // Second call — 200 (idempotent)
        final String groupId2 = given()
                .contentType(ContentType.JSON).body(body)
                .when().post("/workitems/" + parentId + "/spawn")
                .then().statusCode(200).extract().path("groupId");

        assertThat(groupId1).isEqualTo(groupId2);

        // Child count is still 1 — no duplicate
        final List<Map<String, Object>> children = given()
                .when().get("/workitems/" + parentId + "/children")
                .then().statusCode(200)
                .extract().jsonPath().getList("$");
        assertThat(children).hasSize(1);
    }

    @Test
    void differentIdempotencyKey_sameParent_createsNewGroup() {
        final String tmplId = given()
                .contentType(ContentType.JSON)
                .body(Map.of("name", "idem-tmpl2", "category", "test", "createdBy", "test"))
                .when().post("/workitem-templates")
                .then().statusCode(201).extract().path("id");

        final String parentId = given()
                .contentType(ContentType.JSON)
                .body(Map.of("title", "parent2", "category", "test", "createdBy", "test"))
                .when().post("/workitems")
                .then().statusCode(201).extract().path("id");

        final String groupId1 = given()
                .contentType(ContentType.JSON)
                .body(Map.of("idempotencyKey", "key-A-" + UUID.randomUUID(),
                        "children", List.of(Map.of("templateId", tmplId))))
                .when().post("/workitems/" + parentId + "/spawn")
                .then().statusCode(201).extract().path("groupId");

        final String groupId2 = given()
                .contentType(ContentType.JSON)
                .body(Map.of("idempotencyKey", "key-B-" + UUID.randomUUID(),
                        "children", List.of(Map.of("templateId", tmplId))))
                .when().post("/workitems/" + parentId + "/spawn")
                .then().statusCode(201).extract().path("groupId");

        assertThat(groupId1).isNotEqualTo(groupId2);

        // Two children now (one per group)
        final List<Map<String, Object>> children = given()
                .when().get("/workitems/" + parentId + "/children")
                .then().statusCode(200)
                .extract().jsonPath().getList("$");
        assertThat(children).hasSize(2);
    }

    @Test
    void spawnGroups_areScoped_toDifferentGroups() {
        // Verifies GET /spawn-groups/{id} returns ONLY children of that specific group
        final String tmplId = given()
                .contentType(ContentType.JSON)
                .body(Map.of("name", "scope-tmpl", "category", "test", "createdBy", "test"))
                .when().post("/workitem-templates")
                .then().statusCode(201).extract().path("id");

        final String parentId = given()
                .contentType(ContentType.JSON)
                .body(Map.of("title", "scope-parent", "category", "test", "createdBy", "test"))
                .when().post("/workitems")
                .then().statusCode(201).extract().path("id");

        final String groupId1 = given()
                .contentType(ContentType.JSON)
                .body(Map.of("idempotencyKey", "scope-A-" + UUID.randomUUID(),
                        "children", List.of(Map.of("templateId", tmplId, "callerRef", "ref-A"))))
                .when().post("/workitems/" + parentId + "/spawn")
                .then().statusCode(201).extract().path("groupId");

        final String groupId2 = given()
                .contentType(ContentType.JSON)
                .body(Map.of("idempotencyKey", "scope-B-" + UUID.randomUUID(),
                        "children", List.of(Map.of("templateId", tmplId, "callerRef", "ref-B"))))
                .when().post("/workitems/" + parentId + "/spawn")
                .then().statusCode(201).extract().path("groupId");

        // Each group should only see its own child
        final List<Map<String, Object>> group1Children = given()
                .when().get("/spawn-groups/" + groupId1)
                .then().statusCode(200)
                .extract().jsonPath().getList("children");
        assertThat(group1Children).hasSize(1);

        final List<Map<String, Object>> group2Children = given()
                .when().get("/spawn-groups/" + groupId2)
                .then().statusCode(200)
                .extract().jsonPath().getList("children");
        assertThat(group2Children).hasSize(1);

        // Parent has 2 children total
        final List<?> allChildren = given()
                .when().get("/workitems/" + parentId + "/children")
                .then().statusCode(200).extract().jsonPath().getList("$");
        assertThat(allChildren).hasSize(2);
    }
}
