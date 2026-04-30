package io.casehub.work.queues.event;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

@QuarkusTest
class WorkItemQueueEventTest {

    @Inject
    QueueEventCapture capture;

    @BeforeEach
    void reset() {
        capture.clear();
    }

    // ── ADDED ─────────────────────────────────────────────────────────────────

    @Test
    void added_whenWorkItemEntersQueueViaManualLabel() {
        // Queue matches "qevt-added/**"
        final String queueId = createQueue("QEvt Added Test", "qevt-added/**");

        // WorkItem with a manual label matching the queue
        final String itemId = given().contentType(ContentType.JSON)
                .body("""
                        {"title":"Queue added test","createdBy":"alice",
                         "labels":[{"path":"qevt-added/case-1","persistence":"MANUAL","appliedBy":"alice"}]}""")
                .post("/workitems").then().statusCode(201).extract().path("id");

        final var added = capture.eventsOfType(QueueEventType.ADDED);
        assertThat(added).anyMatch(e -> e.workItemId().toString().equals(itemId) &&
                e.queueViewId().toString().equals(queueId));
    }

    @Test
    void added_whenFilterAppliesInferredLabelMatchingQueue() {
        // Filter: category == 'qevt-cat-unique' → apply label 'qevt-inferred/cat'
        given().contentType(ContentType.JSON)
                .body("""
                        {"name":"QEvt cat filter","scope":"ORG","conditionLanguage":"jexl",
                         "conditionExpression":"category == 'qevt-cat-unique'",
                         "actions":[{"type":"APPLY_LABEL","labelPath":"qevt-inferred/cat"}]}""")
                .post("/filters").then().statusCode(201);

        final String queueId = createQueue("QEvt Inferred Queue", "qevt-inferred/**");

        final String itemId = given().contentType(ContentType.JSON)
                .body("""
                        {"title":"Inferred label added test","createdBy":"alice",
                         "category":"qevt-cat-unique"}""")
                .post("/workitems").then().statusCode(201).extract().path("id");

        final var added = capture.eventsOfType(QueueEventType.ADDED);
        assertThat(added).anyMatch(e -> e.workItemId().toString().equals(itemId) &&
                e.queueViewId().toString().equals(queueId));
        assertThat(capture.eventsOfType(QueueEventType.REMOVED)).isEmpty();
    }

    // ── REMOVED ───────────────────────────────────────────────────────────────

    @Test
    void removed_whenManualLabelRemovedFromWorkItem() {
        final String queueId = createQueue("QEvt Removed Test", "qevt-removed/**");

        // Create item with matching label
        final String itemId = given().contentType(ContentType.JSON)
                .body("""
                        {"title":"Queue removed test","createdBy":"alice",
                         "labels":[{"path":"qevt-removed/case-1","persistence":"MANUAL","appliedBy":"alice"}]}""")
                .post("/workitems").then().statusCode(201).extract().path("id");

        capture.clear(); // ignore the ADDED event from creation

        // Remove the label → WorkItem leaves the queue
        given().queryParam("path", "qevt-removed/case-1")
                .delete("/workitems/" + itemId + "/labels")
                .then().statusCode(200);

        final var removed = capture.eventsOfType(QueueEventType.REMOVED);
        assertThat(removed).anyMatch(e -> e.workItemId().toString().equals(itemId) &&
                e.queueViewId().toString().equals(queueId));
        assertThat(capture.eventsOfType(QueueEventType.ADDED)).isEmpty();
    }

    @Test
    void removed_notFiredDuringIntermediateLabelStrip() {
        // Filter: category == 'qevt-stable' → apply 'qevt-stable/marker'
        given().contentType(ContentType.JSON)
                .body("""
                        {"name":"QEvt stable filter","scope":"ORG","conditionLanguage":"jexl",
                         "conditionExpression":"category == 'qevt-stable'",
                         "actions":[{"type":"APPLY_LABEL","labelPath":"qevt-stable/marker"}]}""")
                .post("/filters").then().statusCode(201);

        createQueue("QEvt Stable Queue", "qevt-stable/**");

        final String itemId = given().contentType(ContentType.JSON)
                .body("""
                        {"title":"Stable membership test","createdBy":"alice",
                         "category":"qevt-stable"}""")
                .post("/workitems").then().statusCode(201).extract().path("id");

        capture.clear();

        // Claim the item: triggers lifecycle event → filter strips INFERRED label then re-applies it
        given().contentType(ContentType.JSON)
                .body("{\"assigneeId\":\"alice\"}")
                .put("/workitems/" + itemId + "/claim")
                .then().statusCode(200);

        // REMOVED must NOT fire — the item is still in the queue after re-evaluation
        assertThat(capture.eventsOfType(QueueEventType.REMOVED)).isEmpty();
    }

    // ── CHANGED ───────────────────────────────────────────────────────────────

    @Test
    void changed_whenItemStaysInQueueAfterReEvaluation() {
        // Filter: category == 'qevt-changed' → apply 'qevt-changed/marker'
        given().contentType(ContentType.JSON)
                .body("""
                        {"name":"QEvt changed filter","scope":"ORG","conditionLanguage":"jexl",
                         "conditionExpression":"category == 'qevt-changed'",
                         "actions":[{"type":"APPLY_LABEL","labelPath":"qevt-changed/marker"}]}""")
                .post("/filters").then().statusCode(201);

        final String queueId = createQueue("QEvt Changed Queue", "qevt-changed/**");

        final String itemId = given().contentType(ContentType.JSON)
                .body("""
                        {"title":"Changed test","createdBy":"alice","category":"qevt-changed"}""")
                .post("/workitems").then().statusCode(201).extract().path("id");

        capture.clear(); // ignore ADDED from creation

        // Any lifecycle event triggers re-evaluation: INFERRED label stripped → re-applied → CHANGED
        given().contentType(ContentType.JSON)
                .body("{\"assigneeId\":\"alice\"}")
                .put("/workitems/" + itemId + "/claim")
                .then().statusCode(200);

        final var changed = capture.eventsOfType(QueueEventType.CHANGED);
        assertThat(changed).anyMatch(e -> e.workItemId().toString().equals(itemId) &&
                e.queueViewId().toString().equals(queueId));
        // Must not fire REMOVED — item stayed in queue
        assertThat(capture.eventsOfType(QueueEventType.REMOVED)).isEmpty();
    }

    @Test
    void changed_notAddedAgain_whenItemAlreadyInQueue() {
        given().contentType(ContentType.JSON)
                .body("""
                        {"name":"QEvt no-dup filter","scope":"ORG","conditionLanguage":"jexl",
                         "conditionExpression":"category == 'qevt-nodup'",
                         "actions":[{"type":"APPLY_LABEL","labelPath":"qevt-nodup/marker"}]}""")
                .post("/filters").then().statusCode(201);

        createQueue("QEvt NoDup Queue", "qevt-nodup/**");

        final String itemId = given().contentType(ContentType.JSON)
                .body("""
                        {"title":"No dup added test","createdBy":"alice","category":"qevt-nodup"}""")
                .post("/workitems").then().statusCode(201).extract().path("id");

        capture.clear();

        // Claim → re-evaluation → item still in queue
        given().contentType(ContentType.JSON)
                .body("{\"assigneeId\":\"alice\"}")
                .put("/workitems/" + itemId + "/claim")
                .then().statusCode(200);

        // ADDED should NOT fire again — item was already in queue (fires CHANGED instead)
        assertThat(capture.eventsOfType(QueueEventType.ADDED)).isEmpty();
        assertThat(capture.eventsOfType(QueueEventType.CHANGED)).isNotEmpty();
    }

    // ── No queue match → no events ────────────────────────────────────────────

    @Test
    void noEvent_whenItemHasNoMatchingQueue() {
        // Create item with a label that matches no queue
        given().contentType(ContentType.JSON)
                .body("""
                        {"title":"No queue match","createdBy":"alice",
                         "labels":[{"path":"orphan/unmatched","persistence":"MANUAL","appliedBy":"alice"}]}""")
                .post("/workitems").then().statusCode(201);

        // No queue matches "orphan/**" (no QueueView with that pattern created in this test)
        // So no queue events for "orphan/unmatched" — other tests may fire events for other patterns
        final var orphanAdded = capture.eventsOfType(QueueEventType.ADDED).stream()
                .filter(e -> e.queueName().contains("orphan"))
                .toList();
        assertThat(orphanAdded).isEmpty();
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private String createQueue(final String name, final String pattern) {
        return given().contentType(ContentType.JSON)
                .body(String.format(
                        "{\"name\":\"%s\",\"labelPattern\":\"%s\",\"scope\":\"ORG\"}",
                        name, pattern))
                .post("/queues")
                .then().statusCode(201)
                .extract().path("id");
    }
}
