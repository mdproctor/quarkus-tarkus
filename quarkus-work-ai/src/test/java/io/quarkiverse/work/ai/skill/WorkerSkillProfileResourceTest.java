package io.quarkiverse.work.ai.skill;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import jakarta.transaction.Transactional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class WorkerSkillProfileResourceTest {

    @BeforeEach
    @Transactional
    void cleanup() {
        WorkerSkillProfile.deleteAll();
    }

    @Test
    void createAndGet_happyPath() {
        given()
                .contentType("application/json")
                .body("{\"workerId\": \"alice\", \"narrative\": \"NDA specialist, GDPR expert\"}")
                .when().post("/worker-skill-profiles")
                .then().statusCode(201);

        given()
                .when().get("/worker-skill-profiles/alice")
                .then()
                .statusCode(200)
                .body("workerId", equalTo("alice"))
                .body("narrative", equalTo("NDA specialist, GDPR expert"));
    }

    @Test
    void get_notFound_returns404() {
        given()
                .when().get("/worker-skill-profiles/unknown")
                .then().statusCode(404);
    }

    @Test
    void create_upsert_replacesNarrative() {
        given().contentType("application/json")
                .body("{\"workerId\": \"bob\", \"narrative\": \"original\"}")
                .when().post("/worker-skill-profiles").then().statusCode(201);

        given().contentType("application/json")
                .body("{\"workerId\": \"bob\", \"narrative\": \"updated\"}")
                .when().post("/worker-skill-profiles").then().statusCode(201);

        given().when().get("/worker-skill-profiles/bob")
                .then().statusCode(200).body("narrative", equalTo("updated"));
    }

    @Test
    void listAll_returnsAllProfiles() {
        given().contentType("application/json")
                .body("{\"workerId\": \"alice\", \"narrative\": \"legal\"}")
                .when().post("/worker-skill-profiles").then().statusCode(201);
        given().contentType("application/json")
                .body("{\"workerId\": \"bob\", \"narrative\": \"finance\"}")
                .when().post("/worker-skill-profiles").then().statusCode(201);

        given().when().get("/worker-skill-profiles")
                .then().statusCode(200).body("size()", equalTo(2));
    }

    @Test
    void delete_existingProfile_returns204() {
        given().contentType("application/json")
                .body("{\"workerId\": \"carol\", \"narrative\": \"ops\"}")
                .when().post("/worker-skill-profiles").then().statusCode(201);

        given().when().delete("/worker-skill-profiles/carol")
                .then().statusCode(204);

        given().when().get("/worker-skill-profiles/carol")
                .then().statusCode(404);
    }

    @Test
    void delete_notFound_returns404() {
        given().when().delete("/worker-skill-profiles/nobody")
                .then().statusCode(404);
    }

    @Test
    void create_missingWorkerId_returns400() {
        given().contentType("application/json")
                .body("{\"narrative\": \"legal\"}")
                .when().post("/worker-skill-profiles")
                .then().statusCode(400);
    }
}
