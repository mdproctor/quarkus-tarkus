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
class SpawnE2ETest {

    @Test
    void casehubPattern_callerRef_roundTripsOnEachChild() {
        final String creditTmpl = createTemplate("e2e-credit");
        final String fraudTmpl = createTemplate("e2e-fraud");
        final String compTmpl = createTemplate("e2e-compliance");
        final String parentId = createWorkItem("loan-application");

        final var spawnBody = Map.of(
                "idempotencyKey", "casehub-e2e-" + UUID.randomUUID(),
                "children", List.of(
                        Map.of("templateId", creditTmpl, "callerRef", "case:loan-1/pi:credit"),
                        Map.of("templateId", fraudTmpl, "callerRef", "case:loan-1/pi:fraud"),
                        Map.of("templateId", compTmpl, "callerRef", "case:loan-1/pi:compliance")));

        final Response spawnResp = given()
                .contentType(ContentType.JSON).body(spawnBody)
                .when().post("/workitems/" + parentId + "/spawn")
                .then().statusCode(201)
                .extract().response();

        assertThat(spawnResp.jsonPath().getString("groupId")).isNotNull();

        final List<Map<String, Object>> spawnedChildren = spawnResp.jsonPath().getList("children");
        assertThat(spawnedChildren).hasSize(3);

        final var callerRefs = spawnedChildren.stream()
                .map(c -> (String) c.get("callerRef")).toList();
        assertThat(callerRefs).containsExactlyInAnyOrder(
                "case:loan-1/pi:credit", "case:loan-1/pi:fraud", "case:loan-1/pi:compliance");

        // callerRef persisted on each child
        spawnedChildren.forEach(child -> {
            final String childId = (String) child.get("workItemId");
            final String storedRef = given()
                    .when().get("/workitems/" + childId)
                    .then().statusCode(200).extract().path("callerRef");
            assertThat(storedRef).isEqualTo((String) child.get("callerRef"));
        });

        // PART_OF links exist
        assertThat(given().when().get("/workitems/" + parentId + "/children")
                .then().statusCode(200).extract().jsonPath().getList("$")).hasSize(3);

        // Parent has SPAWNED in audit trail (embedded in GET /workitems/{id} response)
        final List<Map<String, Object>> auditTrail = given()
                .when().get("/workitems/" + parentId)
                .then().statusCode(200).extract().jsonPath().getList("auditTrail");
        assertThat(auditTrail.stream().map(a -> (String) a.get("event")).toList()).contains("SPAWNED");
    }

    @Test
    void nestedSpawn_grandchildren_doNotAppearInParentChildList() {
        final String tmpl = createTemplate("nested-tmpl");
        final String parentId = createWorkItem("nested-parent");

        final Response firstSpawn = given()
                .contentType(ContentType.JSON)
                .body(Map.of("idempotencyKey", "nest-1-" + UUID.randomUUID(),
                        "children", List.of(Map.of("templateId", tmpl))))
                .when().post("/workitems/" + parentId + "/spawn")
                .then().statusCode(201).extract().response();

        final String childId = firstSpawn.jsonPath().getString("children[0].workItemId");

        // Spawn grandchild from child
        given()
                .contentType(ContentType.JSON)
                .body(Map.of("idempotencyKey", "nest-2-" + UUID.randomUUID(),
                        "children", List.of(Map.of("templateId", tmpl))))
                .when().post("/workitems/" + childId + "/spawn")
                .then().statusCode(201);

        // Parent sees only direct child
        assertThat(given().when().get("/workitems/" + parentId + "/children")
                .then().statusCode(200).extract().jsonPath().getList("$")).hasSize(1);

        // Child sees only grandchild
        assertThat(given().when().get("/workitems/" + childId + "/children")
                .then().statusCode(200).extract().jsonPath().getList("$")).hasSize(1);
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
