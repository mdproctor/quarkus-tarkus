package io.casehub.work.examples.spawn;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;

@QuarkusTest
class SpawnScenarioTest {

    @Test
    void run_loanApproval_spawnsThreeParallelChecks() {
        final Response response = given()
                .contentType(ContentType.JSON)
                .when().post("/examples/spawn/run")
                .then().statusCode(200)
                .extract().response();

        assertThat(response.jsonPath().getString("scenario")).isEqualTo("parallel-spawn");

        final List<Map<String, Object>> steps = response.jsonPath().getList("steps");
        assertThat(steps).hasSizeGreaterThanOrEqualTo(3);

        assertThat(response.jsonPath().getString("parentWorkItemId")).isNotNull();

        final List<String> childIds = response.jsonPath().getList("childWorkItemIds");
        assertThat(childIds).hasSize(3);

        childIds.forEach(childId -> {
            final String ref = given()
                    .when().get("/workitems/" + childId)
                    .then().statusCode(200)
                    .extract().path("callerRef");
            assertThat(ref).isNotNull().startsWith("case:loan");
        });
    }
}
