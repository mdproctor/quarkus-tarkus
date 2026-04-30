package io.casehub.work.queues.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

/**
 * Integration tests for {@link QueueMembershipTracker} against a real DB.
 *
 * <p>
 * Verifies that membership state survives across invocations (simulating restarts)
 * by relying on JPA persistence rather than in-memory state.
 */
@QuarkusTest
class QueueMembershipTrackerTest {

    @Inject
    QueueMembershipTracker tracker;

    @Test
    void getBefore_returnsEmpty_forUnknownItem() {
        final Map<UUID, String> before = tracker.getBefore(UUID.randomUUID());
        assertThat(before).isEmpty();
    }

    @Test
    void update_and_getBefore_roundtrip() {
        final UUID workItemId = UUID.randomUUID();
        final UUID q1 = UUID.randomUUID();
        final UUID q2 = UUID.randomUUID();

        tracker.update(workItemId, Map.of(q1, "Legal Queue", q2, "Finance Queue"));

        final Map<UUID, String> before = tracker.getBefore(workItemId);
        assertThat(before)
                .hasSize(2)
                .containsEntry(q1, "Legal Queue")
                .containsEntry(q2, "Finance Queue");
    }

    @Test
    void update_replacesExistingMembership() {
        final UUID workItemId = UUID.randomUUID();
        final UUID q1 = UUID.randomUUID();
        final UUID q2 = UUID.randomUUID();

        tracker.update(workItemId, Map.of(q1, "Queue 1"));
        tracker.update(workItemId, Map.of(q2, "Queue 2")); // replaces

        final Map<UUID, String> before = tracker.getBefore(workItemId);
        assertThat(before).hasSize(1).containsEntry(q2, "Queue 2");
        assertThat(before).doesNotContainKey(q1);
    }

    @Test
    void update_withEmptyMap_clearsEntry() {
        final UUID workItemId = UUID.randomUUID();
        final UUID q1 = UUID.randomUUID();

        tracker.update(workItemId, Map.of(q1, "Legal Queue"));
        tracker.update(workItemId, Map.of()); // item left all queues

        assertThat(tracker.getBefore(workItemId)).isEmpty();
    }

    @Test
    void update_isolatesAcrossWorkItems() {
        final UUID item1 = UUID.randomUUID();
        final UUID item2 = UUID.randomUUID();
        final UUID q1 = UUID.randomUUID();
        final UUID q2 = UUID.randomUUID();

        tracker.update(item1, Map.of(q1, "Legal Queue"));
        tracker.update(item2, Map.of(q2, "Finance Queue"));

        assertThat(tracker.getBefore(item1)).containsOnlyKeys(q1);
        assertThat(tracker.getBefore(item2)).containsOnlyKeys(q2);
    }

    @Test
    void update_withSameQueueTwice_idempotent() {
        final UUID workItemId = UUID.randomUUID();
        final UUID q1 = UUID.randomUUID();

        tracker.update(workItemId, Map.of(q1, "Legal Queue"));
        tracker.update(workItemId, Map.of(q1, "Legal Queue")); // same update

        assertThat(tracker.getBefore(workItemId)).hasSize(1).containsEntry(q1, "Legal Queue");
    }

    @Test
    void persistsSurvivesSimulatedRestart() {
        // Simulate a restart by verifying data is in the DB (not in-memory cache):
        // write via tracker, then verify it's readable — proves DB, not just memory
        final UUID workItemId = UUID.randomUUID();
        final UUID q1 = UUID.randomUUID();

        tracker.update(workItemId, Map.of(q1, "Persistent Queue"));

        // If state were only in-memory, a fresh-lookup would still find it in the same JVM.
        // The test proves the data is persisted to the DB by verifying the entity table directly.
        final long count = io.casehub.work.queues.model.WorkItemQueueMembership
                .count("workItemId = ?1 AND queueViewId = ?2", workItemId, q1);
        assertThat(count).isEqualTo(1);
    }
}
