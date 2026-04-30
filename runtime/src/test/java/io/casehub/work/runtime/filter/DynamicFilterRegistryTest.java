package io.casehub.work.runtime.filter;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * Integration tests for dynamic (DB-persisted) filter rules CRUD.
 * Issue #118, Epic #100.
 */
@QuarkusTest
class DynamicFilterRegistryTest {

    @Test
    void createRule_returns201_withAllFields() {
        given().contentType(ContentType.JSON)
                .body("{\"name\":\"test/dynamic-crud\",\"description\":\"desc\",\"enabled\":true," +
                        "\"condition\":\"workItem.category == 'loan'\",\"events\":[\"ADD\"]," +
                        "\"actionsJson\":\"[{\\\"type\\\":\\\"SET_PRIORITY\\\"," +
                        "\\\"params\\\":{\\\"priority\\\":\\\"HIGH\\\"}}]\"}")
                .post("/filter-rules")
                .then().statusCode(201)
                .body("id", notNullValue())
                .body("name", equalTo("test/dynamic-crud"))
                .body("enabled", equalTo(true))
                .body("condition", equalTo("workItem.category == 'loan'"));
    }

    @Test
    void listRules_returns200_includesCreatedRule() {
        final String name = "list-test-" + System.nanoTime();
        given().contentType(ContentType.JSON)
                .body("{\"name\":\"" + name + "\",\"enabled\":true," +
                        "\"condition\":\"true\",\"events\":[\"ADD\"],\"actionsJson\":\"[]\"}")
                .post("/filter-rules").then().statusCode(201);

        given().get("/filter-rules")
                .then().statusCode(200)
                .body("name", hasItem(name));
    }

    @Test
    void getRule_returns200_forExistingRule() {
        final String id = given().contentType(ContentType.JSON)
                .body("{\"name\":\"get-test-" + System.nanoTime() + "\",\"enabled\":false," +
                        "\"condition\":\"false\",\"events\":[\"UPDATE\"],\"actionsJson\":\"[]\"}")
                .post("/filter-rules").then().statusCode(201).extract().path("id");

        given().get("/filter-rules/" + id)
                .then().statusCode(200)
                .body("id", equalTo(id))
                .body("enabled", equalTo(false));
    }

    @Test
    void getRule_returns404_forUnknownId() {
        given().get("/filter-rules/00000000-0000-0000-0000-000000000000")
                .then().statusCode(404);
    }

    @Test
    void deleteRule_returns204_andRuleIsGone() {
        final String id = given().contentType(ContentType.JSON)
                .body("{\"name\":\"delete-test-" + System.nanoTime() + "\",\"enabled\":true," +
                        "\"condition\":\"true\",\"events\":[\"ADD\"],\"actionsJson\":\"[]\"}")
                .post("/filter-rules").then().statusCode(201).extract().path("id");

        given().delete("/filter-rules/" + id).then().statusCode(204);
        given().get("/filter-rules/" + id).then().statusCode(404);
    }

    @Test
    void deleteRule_returns404_forUnknownId() {
        given().delete("/filter-rules/00000000-0000-0000-0000-000000000000")
                .then().statusCode(404);
    }

    @Test
    void createRule_returns400_whenNameMissing() {
        given().contentType(ContentType.JSON)
                .body("{\"enabled\":true,\"condition\":\"true\",\"events\":[\"ADD\"],\"actionsJson\":\"[]\"}")
                .post("/filter-rules")
                .then().statusCode(400);
    }

    @Test
    void createRule_returns400_whenConditionMissing() {
        given().contentType(ContentType.JSON)
                .body("{\"name\":\"bad-rule\",\"enabled\":true,\"events\":[\"ADD\"],\"actionsJson\":\"[]\"}")
                .post("/filter-rules")
                .then().statusCode(400);
    }
}
