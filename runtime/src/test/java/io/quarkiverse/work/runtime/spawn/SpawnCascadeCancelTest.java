package io.quarkiverse.work.runtime.spawn;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

@QuarkusTest
class SpawnCascadeCancelTest {

    @Test
    void cancelGroup_withoutCascade_leavesChildrenUntouched() {
        final String tmplId = createTemplate("cascade-tmpl-1");
        final String parentId = createWorkItem("cascade-parent-1");
        final String groupId = spawnOne(parentId, tmplId);

        given()
                .when().delete("/workitems/" + parentId + "/spawn-groups/" + groupId)
                .then().statusCode(204);

        // Child still PENDING
        final List<Map<String, Object>> children = given()
                .when().get("/workitems/" + parentId + "/children")
                .then().statusCode(200)
                .extract().jsonPath().getList("$");
        assertThat(children).hasSize(1);

        final String childId = (String) children.get(0).get("id");
        final String childStatus = given()
                .when().get("/workitems/" + childId)
                .then().statusCode(200)
                .extract().path("status");
        assertThat(childStatus).isEqualTo("PENDING");
    }

    @Test
    void cancelGroup_withCascade_cancelsPendingChildren() {
        final String tmplId = createTemplate("cascade-tmpl-2");
        final String parentId = createWorkItem("cascade-parent-2");
        final String groupId = spawnOne(parentId, tmplId);

        given()
                .when().delete("/workitems/" + parentId + "/spawn-groups/" + groupId + "?cancelChildren=true")
                .then().statusCode(204);

        final List<Map<String, Object>> children = given()
                .when().get("/workitems/" + parentId + "/children")
                .then().statusCode(200)
                .extract().jsonPath().getList("$");
        final String childId = (String) children.get(0).get("id");

        final String childStatus = given()
                .when().get("/workitems/" + childId)
                .then().statusCode(200)
                .extract().path("status");
        assertThat(childStatus).isEqualTo("CANCELLED");
    }

    @Test
    void cancelGroup_withCascade_onlyCancelsChildrenFromThatGroup() {
        // Two groups on same parent — cancelling group1 should not affect group2 children
        final String tmplId = createTemplate("cascade-tmpl-scope");
        final String parentId = createWorkItem("cascade-scope-parent");

        final String groupId1 = spawnOne(parentId, tmplId);
        final String groupId2 = spawnOne(parentId, tmplId); // different idempotency key each call

        given()
                .when().delete("/workitems/" + parentId + "/spawn-groups/" + groupId1 + "?cancelChildren=true")
                .then().statusCode(204);

        // Total children: 2. One CANCELLED (from group1), one PENDING (from group2)
        final List<Map<String, Object>> children = given()
                .when().get("/workitems/" + parentId + "/children")
                .then().statusCode(200)
                .extract().jsonPath().getList("$");
        assertThat(children).hasSize(2);

        final long cancelled = children.stream()
                .filter(c -> {
                    final String status = given()
                            .when().get("/workitems/" + c.get("id"))
                            .then().statusCode(200)
                            .extract().path("status");
                    return "CANCELLED".equals(status);
                }).count();
        assertThat(cancelled).isEqualTo(1);
    }

    @Test
    void cancelGroup_returns404_whenGroupNotFound() {
        final String parentId = createWorkItem("cancel-404");
        given()
                .when().delete("/workitems/" + parentId + "/spawn-groups/" + UUID.randomUUID())
                .then().statusCode(404);
    }

    // helpers
    private String createTemplate(final String name) {
        return given().contentType(ContentType.JSON)
                .body(Map.of("name", name, "category", name, "createdBy", "test"))
                .when().post("/workitem-templates")
                .then().statusCode(201).extract().path("id");
    }

    private String createWorkItem(final String category) {
        return given().contentType(ContentType.JSON)
                .body(Map.of("title", "parent-" + category, "category", category, "createdBy", "test"))
                .when().post("/workitems")
                .then().statusCode(201).extract().path("id");
    }

    private String spawnOne(final String parentId, final String tmplId) {
        return given().contentType(ContentType.JSON)
                .body(Map.of("idempotencyKey", "cascade-" + UUID.randomUUID(),
                        "children", List.of(Map.of("templateId", tmplId))))
                .when().post("/workitems/" + parentId + "/spawn")
                .then().statusCode(201).extract().path("groupId");
    }
}
