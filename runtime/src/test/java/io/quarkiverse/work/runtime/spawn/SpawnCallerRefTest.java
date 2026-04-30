package io.casehub.work.runtime.spawn;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

@QuarkusTest
class SpawnCallerRefTest {

    @Test
    void callerRef_roundTrips_whenSetOnCreate() {
        final var body = Map.of(
                "title", "callerRef round-trip test",
                "category", "test",
                "createdBy", "test-system",
                "callerRef", "case:loan-123/pi:credit-1");

        final String id = given()
                .contentType(ContentType.JSON)
                .body(body)
                .when().post("/workitems")
                .then().statusCode(201)
                .extract().path("id");

        final String fetched = given()
                .when().get("/workitems/" + id)
                .then().statusCode(200)
                .extract().path("callerRef");

        assertThat(fetched).isEqualTo("case:loan-123/pi:credit-1");
    }

    @Test
    void callerRef_null_whenNotProvided() {
        final var body = Map.of(
                "title", "no callerRef",
                "category", "test",
                "createdBy", "test-system");

        final String id = given()
                .contentType(ContentType.JSON)
                .body(body)
                .when().post("/workitems")
                .then().statusCode(201)
                .extract().path("id");

        final Object fetched = given()
                .when().get("/workitems/" + id)
                .then().statusCode(200)
                .extract().path("callerRef");

        assertThat(fetched).isNull();
    }

    @Test
    void callerRef_512chars_roundTrips() {
        final String longRef = "x".repeat(512);
        final var body = Map.of(
                "title", "long callerRef test",
                "category", "test",
                "createdBy", "test-system",
                "callerRef", longRef);

        final String id = given()
                .contentType(ContentType.JSON)
                .body(body)
                .when().post("/workitems")
                .then().statusCode(201)
                .extract().path("id");

        final String fetched = given()
                .when().get("/workitems/" + id)
                .then().statusCode(200)
                .extract().path("callerRef");

        assertThat(fetched).isEqualTo(longRef);
    }
}
