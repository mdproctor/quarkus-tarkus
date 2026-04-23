package io.quarkiverse.work.mongodb;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkiverse.work.runtime.model.AuditEntry;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class MongoAuditEntryStoreTest {

    @Inject
    MongoAuditEntryStore store;

    @BeforeEach
    void clearAll() {
        MongoAuditEntryDocument.deleteAll();
    }

    @Test
    void append_and_findByWorkItemId() {
        final UUID workItemId = UUID.randomUUID();

        store.append(entry(workItemId, "CREATED", "system"));
        store.append(entry(workItemId, "ASSIGNED", "alice"));

        final List<AuditEntry> entries = store.findByWorkItemId(workItemId);
        assertThat(entries).hasSize(2);
        assertThat(entries).extracting(e -> e.event)
                .containsExactlyInAnyOrder("CREATED", "ASSIGNED");
    }

    @Test
    void findByWorkItemId_returnsEmpty_forUnknownId() {
        assertThat(store.findByWorkItemId(UUID.randomUUID())).isEmpty();
    }

    @Test
    void findByWorkItemId_isolatesEntriesByWorkItemId() {
        final UUID id1 = UUID.randomUUID();
        final UUID id2 = UUID.randomUUID();

        store.append(entry(id1, "CREATED", "alice"));
        store.append(entry(id2, "CREATED", "bob"));
        store.append(entry(id1, "ASSIGNED", "carol"));

        assertThat(store.findByWorkItemId(id1)).hasSize(2);
        assertThat(store.findByWorkItemId(id2)).hasSize(1);
    }

    @Test
    void append_preservesAllFields() {
        final UUID workItemId = UUID.randomUUID();
        final Instant occurredAt = Instant.now().minusSeconds(5);

        final AuditEntry e = new AuditEntry();
        e.workItemId = workItemId;
        e.event = "COMPLETED";
        e.actor = "alice";
        e.detail = "{\"resolution\":\"approved\"}";
        e.occurredAt = occurredAt;

        store.append(e);
        final AuditEntry loaded = store.findByWorkItemId(workItemId).get(0);

        assertThat(loaded.event).isEqualTo("COMPLETED");
        assertThat(loaded.actor).isEqualTo("alice");
        assertThat(loaded.detail).isEqualTo("{\"resolution\":\"approved\"}");
        assertThat(loaded.occurredAt).isEqualTo(occurredAt.truncatedTo(java.time.temporal.ChronoUnit.MILLIS));
    }

    @Test
    void append_assignsId_whenAbsent() {
        final UUID workItemId = UUID.randomUUID();
        store.append(entry(workItemId, "CREATED", "system"));

        final AuditEntry loaded = store.findByWorkItemId(workItemId).get(0);
        assertThat(loaded.id).isNotNull();
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private AuditEntry entry(final UUID workItemId, final String event, final String actor) {
        final AuditEntry e = new AuditEntry();
        e.workItemId = workItemId;
        e.event = event;
        e.actor = actor;
        e.occurredAt = Instant.now();
        return e;
    }
}
