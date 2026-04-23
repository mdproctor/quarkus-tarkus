package io.quarkiverse.work.queues.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

@QuarkusTest
class QueueStateResourceTest {

    @Test
    void setRelinquishable_true_succeeds() {
        var id = given().contentType(ContentType.JSON)
                .body("""
                        {"title":"Soft assign test","createdBy":"alice","assigneeId":"alice"}""")
                .post("/workitems").then().statusCode(201).extract().path("id");

        given().contentType(ContentType.JSON).body("""
                {"relinquishable":true}""")
                .put("/workitems/" + id + "/relinquishable").then()
                .statusCode(200).body("relinquishable", equalTo(true));
    }

    @Test
    void setRelinquishable_false_clearsFlag() {
        var id = given().contentType(ContentType.JSON)
                .body("""
                        {"title":"Clear flag test","createdBy":"alice","assigneeId":"alice"}""")
                .post("/workitems").then().statusCode(201).extract().path("id");

        given().contentType(ContentType.JSON).body("""
                {"relinquishable":true}""")
                .put("/workitems/" + id + "/relinquishable").then().statusCode(200);

        given().contentType(ContentType.JSON).body("""
                {"relinquishable":false}""")
                .put("/workitems/" + id + "/relinquishable").then()
                .statusCode(200).body("relinquishable", equalTo(false));
    }

    @Test
    void relinquishable_unknownWorkItem_returns404() {
        given().contentType(ContentType.JSON).body("""
                {"relinquishable":true}""")
                .put("/workitems/00000000-0000-0000-0000-000000000000/relinquishable").then()
                .statusCode(404);
    }

    @Test
    void pickup_pendingItem_claimsIt() {
        var id = given().contentType(ContentType.JSON)
                .body("""
                        {"title":"Pickup pending test","createdBy":"alice"}""")
                .post("/workitems").then().statusCode(201).extract().path("id");

        given().put("/workitems/" + id + "/pickup?claimant=bob")
                .then().statusCode(200)
                .body("assigneeId", equalTo("bob"))
                .body("status", equalTo("ASSIGNED"));
    }

    @Test
    void pickup_relinquishableAssignedItem_succeedsAndClearsFlag() {
        // Create and claim a WorkItem
        var id = given().contentType(ContentType.JSON)
                .body("""
                        {"title":"Pickup relinquishable test","createdBy":"alice"}""")
                .post("/workitems").then().statusCode(201).extract().path("id");

        given().put("/workitems/" + id + "/claim?claimant=alice").then().statusCode(200);

        // Mark as relinquishable
        given().contentType(ContentType.JSON)
                .body("""
                        {"relinquishable":true}""")
                .put("/workitems/" + id + "/relinquishable").then().statusCode(200);

        // Bob picks it up from the queue
        given().put("/workitems/" + id + "/pickup?claimant=bob")
                .then().statusCode(200)
                .body("assigneeId", equalTo("bob"))
                .body("status", equalTo("ASSIGNED"));

        // Flag is now cleared after bob's pickup — charlie cannot immediately take it
        // (bob has not set relinquishable, so the flag is false)
        given().put("/workitems/" + id + "/pickup?claimant=charlie")
                .then().statusCode(409); // relinquishable was cleared on bob's pickup
    }

    @Test
    void pickup_nonRelinquishableAssignedItem_returns409() {
        var id = given().contentType(ContentType.JSON)
                .body("""
                        {"title":"Non-relinquishable test","createdBy":"alice"}""")
                .post("/workitems").then().statusCode(201).extract().path("id");

        given().put("/workitems/" + id + "/claim?claimant=alice").then().statusCode(200);

        // No relinquishable flag set — pickup should be rejected
        given().put("/workitems/" + id + "/pickup?claimant=bob")
                .then().statusCode(409);
    }

    @Test
    void pickup_unknownWorkItem_returns404() {
        given().put("/workitems/00000000-0000-0000-0000-000000000000/pickup?claimant=bob")
                .then().statusCode(404);
    }
}
