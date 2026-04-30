package io.casehub.work.runtime.spawn;

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
class WorkItemSpawnResourceTest {

    @Test
    void spawn_createsChildren_withPartOfLinks() {
        final String tmplId1 = createTemplate("credit-check");
        final String tmplId2 = createTemplate("fraud-check");
        final String parentId = createWorkItem("loan-application");

        final var spawnBody = Map.of(
                "idempotencyKey", "test-spawn-" + UUID.randomUUID(),
                "children", List.of(
                        Map.of("templateId", tmplId1, "callerRef", "case:l1/pi:c1"),
                        Map.of("templateId", tmplId2, "callerRef", "case:l1/pi:f2")));

        final Response response = given()
                .contentType(ContentType.JSON)
                .body(spawnBody)
                .when().post("/workitems/" + parentId + "/spawn")
                .then().statusCode(201)
                .extract().response();

        assertThat(response.jsonPath().getString("groupId")).isNotNull();
        final List<Map<String, Object>> children = response.jsonPath().getList("children");
        assertThat(children).hasSize(2);
        assertThat(children.get(0).get("callerRef")).isEqualTo("case:l1/pi:c1");
        assertThat(children.get(1).get("callerRef")).isEqualTo("case:l1/pi:f2");

        final List<Map<String, Object>> childList = given()
                .when().get("/workitems/" + parentId + "/children")
                .then().statusCode(200)
                .extract().jsonPath().getList("$");
        assertThat(childList).hasSize(2);

        final String child1Id = (String) children.get(0).get("workItemId");
        final String fetchedRef = given()
                .when().get("/workitems/" + child1Id)
                .then().statusCode(200)
                .extract().path("callerRef");
        assertThat(fetchedRef).isEqualTo("case:l1/pi:c1");
    }

    @Test
    void spawn_returns404_whenParentNotFound() {
        given()
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "idempotencyKey", "key-" + UUID.randomUUID(),
                        "children", List.of(Map.of("templateId", UUID.randomUUID().toString()))))
                .when().post("/workitems/" + UUID.randomUUID() + "/spawn")
                .then().statusCode(404);
    }

    @Test
    void spawn_returns400_whenChildrenEmpty() {
        final String parentId = createWorkItem("test");
        given()
                .contentType(ContentType.JSON)
                .body(Map.of("idempotencyKey", "key-1", "children", List.of()))
                .when().post("/workitems/" + parentId + "/spawn")
                .then().statusCode(400);
    }

    @Test
    void spawn_returns400_whenNoIdempotencyKey() {
        final String parentId = createWorkItem("test");
        given()
                .contentType(ContentType.JSON)
                .body(Map.of("children", List.of(
                        Map.of("templateId", UUID.randomUUID().toString()))))
                .when().post("/workitems/" + parentId + "/spawn")
                .then().statusCode(400);
    }

    @Test
    void spawn_returns422_whenTemplateNotFound() {
        final String parentId = createWorkItem("test");
        given()
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "idempotencyKey", "key-" + UUID.randomUUID(),
                        "children", List.of(Map.of("templateId", UUID.randomUUID().toString()))))
                .when().post("/workitems/" + parentId + "/spawn")
                .then().statusCode(422);
    }

    @Test
    void spawnGroups_listed_forParent() {
        final String tmplId = createTemplate("tmpl-for-list");
        final String parentId = createWorkItem("loan");
        final String key = "list-test-" + UUID.randomUUID();

        given()
                .contentType(ContentType.JSON)
                .body(Map.of("idempotencyKey", key,
                        "children", List.of(Map.of("templateId", tmplId))))
                .when().post("/workitems/" + parentId + "/spawn")
                .then().statusCode(201);

        final List<Map<String, Object>> groups = given()
                .when().get("/workitems/" + parentId + "/spawn-groups")
                .then().statusCode(200)
                .extract().jsonPath().getList("$");

        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).get("idempotencyKey")).isEqualTo(key);
    }

    @Test
    void spawnGroup_getById_returnsGroupAndChildren() {
        final String tmplId = createTemplate("tmpl-get");
        final String parentId = createWorkItem("loan");

        final String groupId = given()
                .contentType(ContentType.JSON)
                .body(Map.of("idempotencyKey", "get-test-" + UUID.randomUUID(),
                        "children", List.of(Map.of("templateId", tmplId, "callerRef", "ref-1"))))
                .when().post("/workitems/" + parentId + "/spawn")
                .then().statusCode(201)
                .extract().path("groupId");

        final io.restassured.response.Response groupResp = given()
                .when().get("/spawn-groups/" + groupId)
                .then().statusCode(200)
                .extract().response();

        assertThat(groupResp.jsonPath().getString("id")).isEqualTo(groupId);
        assertThat(groupResp.jsonPath().getString("parentId")).isEqualTo(parentId);
        final List<Map<String, Object>> children = groupResp.jsonPath().getList("children");
        assertThat(children).hasSize(1);
    }

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
}
