package io.quarkiverse.workitems.runtime.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

@QuarkusTest
class LabelEndpointTest {

    @Test
    void createWorkItem_withManualLabel_returnedInResponse() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "title": "Review contract",
                          "createdBy": "alice",
                          "labels": [
                            {"path": "legal/contracts", "persistence": "MANUAL", "appliedBy": "alice"}
                          ]
                        }
                        """)
                .post("/workitems")
                .then()
                .statusCode(201)
                .body("labels", hasSize(1))
                .body("labels[0].path", equalTo("legal/contracts"))
                .body("labels[0].persistence", equalTo("MANUAL"))
                .body("labels[0].appliedBy", equalTo("alice"));
    }

    @Test
    void createWorkItem_withInferredLabel_returns400() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "title": "Review contract",
                          "createdBy": "alice",
                          "labels": [
                            {"path": "legal/contracts", "persistence": "INFERRED", "appliedBy": null}
                          ]
                        }
                        """)
                .post("/workitems")
                .then()
                .statusCode(400);
    }

    @Test
    void createWorkItem_withNoLabels_returnsEmptyList() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "title": "No labels",
                          "createdBy": "bob"
                        }
                        """)
                .post("/workitems")
                .then()
                .statusCode(201)
                .body("labels", hasSize(0));
    }

    @Test
    void createWorkItem_withMultipleLabels_allReturned() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "title": "Multi-label item",
                          "createdBy": "alice",
                          "labels": [
                            {"path": "legal/contracts", "persistence": "MANUAL", "appliedBy": "alice"},
                            {"path": "priority/high",   "persistence": "MANUAL", "appliedBy": "alice"}
                          ]
                        }
                        """)
                .post("/workitems")
                .then()
                .statusCode(201)
                .body("labels", hasSize(2));
    }
}
