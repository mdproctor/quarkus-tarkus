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
