package io.quarkiverse.workitems.runtime.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * Integration tests for WorkItemNote endpoints.
 *
 * <p>
 * Notes are internal operational annotations — distinct from the immutable
 * structured audit log and from external issue tracker comments. They capture
 * the "why" of operational decisions: why was this delegated, what was found
 * during review, what context the next assignee needs.
 */
@QuarkusTest
class WorkItemNoteTest {

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String createWorkItem() {
        return given().contentType(ContentType.JSON)
                .body("{\"title\":\"Note test item\",\"createdBy\":\"system\"}")
                .post("/workitems")
                .then().statusCode(201)
                .extract().path("id");
    }

    private String addNote(final String itemId, final String content, final String author) {
        return given().contentType(ContentType.JSON)
                .body("{\"content\":\"" + content + "\",\"author\":\"" + author + "\"}")
                .post("/workitems/" + itemId + "/notes")
                .then().statusCode(201)
                .extract().path("id");
    }

    // ── POST /workitems/{id}/notes ────────────────────────────────────────────

    @Test
    void addNote_returns201_withIdAndTimestamp() {
        final String itemId = createWorkItem();

        given().contentType(ContentType.JSON)
                .body("{\"content\":\"Delegated to Carol — Alice is on leave\",\"author\":\"alice\"}")
                .post("/workitems/" + itemId + "/notes")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("workItemId", equalTo(itemId))
                .body("content", equalTo("Delegated to Carol — Alice is on leave"))
                .body("author", equalTo("alice"))
                .body("createdAt", notNullValue())
                .body("editedAt", nullValue());
    }

    @Test
    void addNote_returns400_whenContentBlank() {
        final String itemId = createWorkItem();

        given().contentType(ContentType.JSON)
                .body("{\"content\":\"\",\"author\":\"alice\"}")
                .post("/workitems/" + itemId + "/notes")
                .then()
                .statusCode(400);
    }

    @Test
    void addNote_returns400_whenAuthorMissing() {
        final String itemId = createWorkItem();

        given().contentType(ContentType.JSON)
                .body("{\"content\":\"some note\"}")
                .post("/workitems/" + itemId + "/notes")
                .then()
                .statusCode(400);
    }

    @Test
    void addNote_multipleNotes_allPersist() {
        final String itemId = createWorkItem();

        addNote(itemId, "First note", "alice");
        addNote(itemId, "Second note", "bob");

        given().get("/workitems/" + itemId + "/notes")
                .then().statusCode(200)
                .body("$", hasSize(2));
    }

    // ── GET /workitems/{id}/notes ─────────────────────────────────────────────

    @Test
    void listNotes_returnsEmpty_forNewWorkItem() {
        final String itemId = createWorkItem();

        given().get("/workitems/" + itemId + "/notes")
                .then().statusCode(200)
                .body("$", empty());
    }

    @Test
    void listNotes_returnsChronological_oldestFirst() {
        final String itemId = createWorkItem();

        addNote(itemId, "First", "alice");
        addNote(itemId, "Second", "bob");

        given().get("/workitems/" + itemId + "/notes")
                .then().statusCode(200)
                .body("[0].content", equalTo("First"))
                .body("[1].content", equalTo("Second"));
    }

    @Test
    void listNotes_isolatedAcrossWorkItems() {
        final String item1 = createWorkItem();
        final String item2 = createWorkItem();

        addNote(item1, "Note on item 1", "alice");

        given().get("/workitems/" + item2 + "/notes")
                .then().statusCode(200)
                .body("$", empty());
    }

    // ── PUT /workitems/{id}/notes/{noteId} ────────────────────────────────────

    @Test
    void editNote_updatesContent_setsEditedAt() {
        final String itemId = createWorkItem();
        final String noteId = addNote(itemId, "Original content", "alice");

        given().contentType(ContentType.JSON)
                .body("{\"content\":\"Revised content — found additional context\"}")
                .put("/workitems/" + itemId + "/notes/" + noteId)
                .then()
                .statusCode(200)
                .body("content", equalTo("Revised content — found additional context"))
                .body("editedAt", notNullValue());
    }

    @Test
    void editNote_returns404_forUnknownNote() {
        final String itemId = createWorkItem();

        given().contentType(ContentType.JSON)
                .body("{\"content\":\"irrelevant\"}")
                .put("/workitems/" + itemId + "/notes/" + java.util.UUID.randomUUID())
                .then()
                .statusCode(404);
    }

    @Test
    void editNote_returns400_whenContentBlank() {
        final String itemId = createWorkItem();
        final String noteId = addNote(itemId, "Original", "alice");

        given().contentType(ContentType.JSON)
                .body("{\"content\":\"\"}")
                .put("/workitems/" + itemId + "/notes/" + noteId)
                .then()
                .statusCode(400);
    }

    // ── DELETE /workitems/{id}/notes/{noteId} ─────────────────────────────────

    @Test
    void deleteNote_returns204_andNoteIsGone() {
        final String itemId = createWorkItem();
        final String noteId = addNote(itemId, "To be deleted", "alice");

        given().delete("/workitems/" + itemId + "/notes/" + noteId)
                .then().statusCode(204);

        given().get("/workitems/" + itemId + "/notes")
                .then().statusCode(200).body("$", empty());
    }

    @Test
    void deleteNote_returns404_forUnknownNote() {
        final String itemId = createWorkItem();

        given().delete("/workitems/" + itemId + "/notes/" + java.util.UUID.randomUUID())
                .then().statusCode(404);
    }

    @Test
    void deleteNote_onlyRemovesTargetNote() {
        final String itemId = createWorkItem();
        final String note1 = addNote(itemId, "Keep this", "alice");
        final String note2 = addNote(itemId, "Delete this", "bob");

        given().delete("/workitems/" + itemId + "/notes/" + note2)
                .then().statusCode(204);

        given().get("/workitems/" + itemId + "/notes")
                .then().statusCode(200)
                .body("$", hasSize(1))
                .body("[0].id", equalTo(note1));
    }
}
