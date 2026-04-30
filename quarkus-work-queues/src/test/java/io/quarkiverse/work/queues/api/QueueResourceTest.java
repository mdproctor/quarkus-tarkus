package io.casehub.work.queues.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

@QuarkusTest
class QueueResourceTest {

    @Test
    void createQueueView_returnsId() {
        given().contentType(ContentType.JSON)
                .body("""
                        {"name":"Legal triage","labelPattern":"legal/**",
                         "scope":"TEAM","sortField":"createdAt","sortDirection":"ASC"}""")
                .post("/queues").then().statusCode(201)
                .body("id", notNullValue()).body("name", equalTo("Legal triage"));
    }

    @Test
    void listQueues_returnsCreated() {
        given().contentType(ContentType.JSON)
                .body("""
                        {"name":"List queue test","labelPattern":"intake/**","scope":"ORG"}""")
                .post("/queues").then().statusCode(201);
        given().get("/queues").then().statusCode(200).body("name", hasItem("List queue test"));
    }

    @Test
    void deleteQueueView_removesIt() {
        var id = given().contentType(ContentType.JSON)
                .body("""
                        {"name":"Delete me queue","labelPattern":"x/**","scope":"ORG"}""")
                .post("/queues").then().statusCode(201).extract().path("id");
        given().delete("/queues/" + id).then().statusCode(204);
    }

    @Test
    void getQueue_returnsWorkItemsMatchingLabel() {
        // Create filter: HIGH → queue-test/unique label
        given().contentType(ContentType.JSON)
                .body("""
                        {"name":"QueueView fill","scope":"ORG","conditionLanguage":"jexl",
                         "conditionExpression":"priority == 'HIGH'",
                         "actions":[{"type":"APPLY_LABEL","labelPath":"queue-test/unique"}]}""")
                .post("/filters").then().statusCode(201);

        // Create queue view
        var queueId = given().contentType(ContentType.JSON)
                .body("""
                        {"name":"Queue member test","labelPattern":"queue-test/**",
                         "scope":"ORG","sortField":"createdAt","sortDirection":"ASC"}""")
                .post("/queues").then().statusCode(201).extract().path("id");

        // Create HIGH WorkItem — filter fires, INFERRED label applied
        given().contentType(ContentType.JSON)
                .body("""
                        {"title":"Queue test member","priority":"HIGH","createdBy":"alice"}""")
                .post("/workitems").then().statusCode(201);

        // Queue returns the WorkItem
        given().get("/queues/" + queueId).then().statusCode(200)
                .body("title", hasItem("Queue test member"));
    }

    @Test
    void getQueueView_unknownId_returns404() {
        given().get("/queues/00000000-0000-0000-0000-000000000000").then().statusCode(404);
    }

    @Test
    void getQueue_withAdditionalConditions_filtersToMatchingStatusOnly() {
        // Create filter: HIGH priority → unique label for this test
        given().contentType(ContentType.JSON)
                .body("""
                        {"name":"Additional conditions test filter","scope":"ORG",
                         "conditionLanguage":"jexl","conditionExpression":"priority == 'HIGH'",
                         "actions":[{"type":"APPLY_LABEL","labelPath":"cond-test/pending-only"}]}""")
                .post("/filters").then().statusCode(201);

        // Queue view: only PENDING items within the label
        var queueId = given().contentType(ContentType.JSON)
                .body("""
                        {"name":"Pending only queue","labelPattern":"cond-test/**",
                         "additionalConditions":"status == 'PENDING'",
                         "scope":"ORG"}""")
                .post("/queues").then().statusCode(201).extract().path("id");

        // PENDING HIGH item — gets label, passes additionalConditions
        given().contentType(ContentType.JSON)
                .body("""
                        {"title":"Pending high item","priority":"HIGH","createdBy":"alice"}""")
                .post("/workitems").then().statusCode(201);

        // HIGH item that gets claimed (becomes ASSIGNED) — gets label, fails additionalConditions
        var assignedId = given().contentType(ContentType.JSON)
                .body("""
                        {"title":"Assigned high item","priority":"HIGH","createdBy":"alice"}""")
                .post("/workitems").then().statusCode(201).extract().path("id");

        given().put("/workitems/" + assignedId + "/claim?claimant=bob")
                .then().statusCode(200);

        // Queue returns only the PENDING item — additionalConditions filters out ASSIGNED
        given().get("/queues/" + queueId).then()
                .statusCode(200)
                .body("title", hasItem("Pending high item"))
                .body("title", not(hasItem("Assigned high item")));
    }
}
