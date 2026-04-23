package io.quarkiverse.work.runtime.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * Integration and end-to-end tests for WorkItem relation graph.
 *
 * <h2>What relations enable in UIs</h2>
 * <ul>
 * <li>PART_OF → tree navigation, breadcrumb trails, group summaries</li>
 * <li>Filtering: "show only top-level" = WorkItems with no outgoing PART_OF</li>
 * <li>Group progress: X of Y children COMPLETED</li>
 * <li>Any custom type (TRIGGERED_BY, APPROVED_BY) — no registration required</li>
 * </ul>
 *
 * <h2>Test tiers</h2>
 * <ul>
 * <li><strong>Unit</strong> — WorkItemRelationTypeTest (pure Java, relation type constants)</li>
 * <li><strong>Integration</strong> — CRUD, idempotency, isolation</li>
 * <li><strong>Happy path</strong> — build a tree, navigate parent/children</li>
 * <li><strong>E2E</strong> — cycle prevention, custom types, incoming relations</li>
 * </ul>
 */
@QuarkusTest
class WorkItemRelationTest {

    // ── POST /workitems/{id}/relations ────────────────────────────────────────

    @Test
    void addRelation_returns201_withAllFields() {
        final String child = createWorkItem("Child task");
        final String parent = createWorkItem("Parent epic");

        given().contentType(ContentType.JSON)
                .body("{\"targetId\":\"" + parent + "\",\"relationType\":\"PART_OF\",\"createdBy\":\"alice\"}")
                .post("/workitems/" + child + "/relations")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("sourceId", equalTo(child))
                .body("targetId", equalTo(parent))
                .body("relationType", equalTo("PART_OF"))
                .body("createdBy", equalTo("alice"))
                .body("createdAt", notNullValue());
    }

    @Test
    void addRelation_returns400_whenTargetIdMissing() {
        final String id = createWorkItem("Item");
        given().contentType(ContentType.JSON)
                .body("{\"relationType\":\"PART_OF\",\"createdBy\":\"alice\"}")
                .post("/workitems/" + id + "/relations")
                .then().statusCode(400);
    }

    @Test
    void addRelation_returns400_whenRelationTypeMissing() {
        final String id = createWorkItem("Item");
        final String other = createWorkItem("Other");
        given().contentType(ContentType.JSON)
                .body("{\"targetId\":\"" + other + "\",\"createdBy\":\"alice\"}")
                .post("/workitems/" + id + "/relations")
                .then().statusCode(400);
    }

    @Test
    void addRelation_acceptsCustomType_withoutRegistration() {
        final String source = createWorkItem("Trigger");
        final String target = createWorkItem("Target");
        given().contentType(ContentType.JSON)
                .body("{\"targetId\":\"" + target + "\",\"relationType\":\"TRIGGERED_BY\",\"createdBy\":\"system\"}")
                .post("/workitems/" + source + "/relations")
                .then()
                .statusCode(201)
                .body("relationType", equalTo("TRIGGERED_BY"));
    }

    @Test
    void addRelation_isIdempotent_secondAddReturns409() {
        final String child = createWorkItem("Child");
        final String parent = createWorkItem("Parent");
        final String body = "{\"targetId\":\"" + parent + "\",\"relationType\":\"PART_OF\",\"createdBy\":\"alice\"}";

        given().contentType(ContentType.JSON).body(body)
                .post("/workitems/" + child + "/relations").then().statusCode(201);

        given().contentType(ContentType.JSON).body(body)
                .post("/workitems/" + child + "/relations").then().statusCode(409);
    }

    // ── GET /workitems/{id}/relations (outgoing) ──────────────────────────────

    @Test
    void listRelations_returnsEmpty_forNewWorkItem() {
        given().get("/workitems/" + createWorkItem("Isolated") + "/relations")
                .then().statusCode(200).body("$", empty());
    }

    @Test
    void listRelations_returnsOutgoingRelations() {
        final String child = createWorkItem("Child");
        final String parent = createWorkItem("Parent");

        given().contentType(ContentType.JSON)
                .body("{\"targetId\":\"" + parent + "\",\"relationType\":\"PART_OF\",\"createdBy\":\"alice\"}")
                .post("/workitems/" + child + "/relations").then().statusCode(201);

        given().get("/workitems/" + child + "/relations")
                .then().statusCode(200)
                .body("$", hasSize(1))
                .body("[0].relationType", equalTo("PART_OF"))
                .body("[0].targetId", equalTo(parent));
    }

    // ── GET /workitems/{id}/relations/incoming ────────────────────────────────

    @Test
    void listIncomingRelations_returnsRelationsTargetingThisItem() {
        final String parent = createWorkItem("Epic parent");
        final String child1 = createWorkItem("Child 1");
        final String child2 = createWorkItem("Child 2");

        given().contentType(ContentType.JSON)
                .body("{\"targetId\":\"" + parent + "\",\"relationType\":\"PART_OF\",\"createdBy\":\"alice\"}")
                .post("/workitems/" + child1 + "/relations").then().statusCode(201);
        given().contentType(ContentType.JSON)
                .body("{\"targetId\":\"" + parent + "\",\"relationType\":\"PART_OF\",\"createdBy\":\"alice\"}")
                .post("/workitems/" + child2 + "/relations").then().statusCode(201);

        given().get("/workitems/" + parent + "/relations/incoming")
                .then().statusCode(200)
                .body("$", hasSize(2))
                .body("sourceId", hasItem(child1))
                .body("sourceId", hasItem(child2));
    }

    // ── GET /workitems/{id}/children (convenience) ────────────────────────────

    @Test
    void children_returnsPartOfIncomingItems() {
        final String parent = createWorkItem("Group");
        final String child1 = createWorkItem("Sub-task 1");
        final String child2 = createWorkItem("Sub-task 2");
        final String unrelated = createWorkItem("Unrelated");

        addPartOf(child1, parent);
        addPartOf(child2, parent);
        // unrelated has no PART_OF relation to parent

        given().get("/workitems/" + parent + "/children")
                .then().statusCode(200)
                .body("$", hasSize(2))
                .body("id", hasItem(child1))
                .body("id", hasItem(child2));
    }

    @Test
    void children_returnsEmpty_forLeafNode() {
        given().get("/workitems/" + createWorkItem("Leaf") + "/children")
                .then().statusCode(200).body("$", empty());
    }

    // ── GET /workitems/{id}/parent (convenience) ──────────────────────────────

    @Test
    void parent_returnsParentWorkItem_viaPartOf() {
        final String child = createWorkItem("Child task");
        final String parent = createWorkItem("Parent epic");
        addPartOf(child, parent);

        given().get("/workitems/" + child + "/parent")
                .then().statusCode(200)
                .body("id", equalTo(parent));
    }

    @Test
    void parent_returns404_whenNoPartOfRelation() {
        given().get("/workitems/" + createWorkItem("Root") + "/parent")
                .then().statusCode(404);
    }

    // ── DELETE /workitems/{id}/relations/{relationId} ─────────────────────────

    @Test
    void deleteRelation_returns204_andRelationIsGone() {
        final String child = createWorkItem("Child");
        final String parent = createWorkItem("Parent");

        final String relationId = given().contentType(ContentType.JSON)
                .body("{\"targetId\":\"" + parent + "\",\"relationType\":\"PART_OF\",\"createdBy\":\"alice\"}")
                .post("/workitems/" + child + "/relations")
                .then().statusCode(201).extract().path("id");

        given().delete("/workitems/" + child + "/relations/" + relationId)
                .then().statusCode(204);

        given().get("/workitems/" + child + "/relations")
                .then().statusCode(200).body("$", empty());
    }

    @Test
    void deleteRelation_returns404_forUnknownRelation() {
        given().delete("/workitems/" + createWorkItem("Item") + "/relations/00000000-0000-0000-0000-000000000000")
                .then().statusCode(404);
    }

    // ── E2E: cycle prevention for PART_OF ────────────────────────────────────

    @Test
    void addRelation_returns400_whenPartOfCreatesDirectCycle() {
        final String a = createWorkItem("A");
        final String b = createWorkItem("B");

        addPartOf(a, b); // A is child of B

        // B PART_OF A would create a cycle
        given().contentType(ContentType.JSON)
                .body("{\"targetId\":\"" + a + "\",\"relationType\":\"PART_OF\",\"createdBy\":\"alice\"}")
                .post("/workitems/" + b + "/relations")
                .then().statusCode(400)
                .body("error", containsString("cycle"));
    }

    @Test
    void addRelation_returns400_whenPartOfCreatesIndirectCycle() {
        final String a = createWorkItem("A");
        final String b = createWorkItem("B");
        final String c = createWorkItem("C");

        addPartOf(a, b); // A → B
        addPartOf(b, c); // B → C

        // C PART_OF A would create cycle: A → B → C → A
        given().contentType(ContentType.JSON)
                .body("{\"targetId\":\"" + a + "\",\"relationType\":\"PART_OF\",\"createdBy\":\"alice\"}")
                .post("/workitems/" + c + "/relations")
                .then().statusCode(400)
                .body("error", containsString("cycle"));
    }

    @Test
    void addRelation_returns400_whenPartOfSelf() {
        final String id = createWorkItem("Self-referencing");
        given().contentType(ContentType.JSON)
                .body("{\"targetId\":\"" + id + "\",\"relationType\":\"PART_OF\",\"createdBy\":\"alice\"}")
                .post("/workitems/" + id + "/relations")
                .then().statusCode(400)
                .body("error", containsString("cycle"));
    }

    @Test
    void cycleCheck_onlyAppliesTo_partOfRelations() {
        // Non-PART_OF relations don't require cycle checking
        final String a = createWorkItem("A");
        given().contentType(ContentType.JSON)
                .body("{\"targetId\":\"" + a + "\",\"relationType\":\"RELATES_TO\",\"createdBy\":\"alice\"}")
                .post("/workitems/" + a + "/relations") // self-loop in RELATES_TO is allowed
                .then().statusCode(201);
    }

    // ── E2E: tree navigation (happy path) ────────────────────────────────────

    @Test
    void e2e_buildTree_navigateUpAndDown() {
        final String root = createWorkItem("Q2 Security Review (Epic)");
        final String child1 = createWorkItem("Threat modelling");
        final String child2 = createWorkItem("Pen test coordination");
        final String grandchild = createWorkItem("Review OWASP findings");

        addPartOf(child1, root);
        addPartOf(child2, root);
        addPartOf(grandchild, child2);

        // Navigate down: root has 2 direct children
        given().get("/workitems/" + root + "/children")
                .then().statusCode(200).body("$", hasSize(2));

        // Navigate down further: child2 has 1 child
        given().get("/workitems/" + child2 + "/children")
                .then().statusCode(200)
                .body("$", hasSize(1))
                .body("[0].id", equalTo(grandchild));

        // Navigate up: grandchild's parent is child2
        given().get("/workitems/" + grandchild + "/parent")
                .then().statusCode(200).body("id", equalTo(child2));

        // Navigate up again: child2's parent is root
        given().get("/workitems/" + child2 + "/parent")
                .then().statusCode(200).body("id", equalTo(root));

        // Root has no parent
        given().get("/workitems/" + root + "/parent")
                .then().statusCode(404);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String createWorkItem(final String title) {
        return given().contentType(ContentType.JSON)
                .body("{\"title\":\"" + title + "\",\"createdBy\":\"test\"}")
                .post("/workitems").then().statusCode(201).extract().path("id");
    }

    private void addPartOf(final String childId, final String parentId) {
        given().contentType(ContentType.JSON)
                .body("{\"targetId\":\"" + parentId + "\",\"relationType\":\"PART_OF\",\"createdBy\":\"test\"}")
                .post("/workitems/" + childId + "/relations")
                .then().statusCode(201);
    }
}
