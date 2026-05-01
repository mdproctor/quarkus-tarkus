package io.casehub.work.queues.postgres.broadcaster;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.UserTransaction;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import io.casehub.work.queues.event.QueueEventType;
import io.casehub.work.queues.event.WorkItemQueueEvent;
import io.casehub.work.queues.service.WorkItemQueueEventBroadcaster;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Integration tests for {@link PostgresWorkItemQueueEventBroadcaster} against a real
 * PostgreSQL container. Verifies the full LISTEN/NOTIFY path for queue events.
 *
 * <p>
 * Events are fired within a JTA transaction (via {@link UserTransaction}) because
 * {@link PostgresWorkItemQueueEventBroadcaster#onEvent} uses
 * {@code @Observes(during = AFTER_SUCCESS)} — the pg_notify only fires after a successful
 * commit, matching production semantics where queue events arise from WorkItem mutations.
 *
 * <h2>Coverage</h2>
 * <ul>
 *   <li><b>Wiring:</b> CDI selects PostgresWorkItemQueueEventBroadcaster over local impl
 *   <li><b>Happy path:</b> queue event reaches SSE stream via PostgreSQL channel
 *   <li><b>Correctness:</b> filter by queueViewId; all event types (ADDED/CHANGED/REMOVED)
 *   <li><b>Robustness:</b> AFTER_SUCCESS — rolled-back events not published; multiple events delivered
 * </ul>
 */
@QuarkusTest
@QuarkusTestResource(PostgresQueueBroadcasterTestResource.class)
class PostgresQueueBroadcasterIT {

    @Inject
    WorkItemQueueEventBroadcaster broadcaster;

    @Inject
    Event<WorkItemQueueEvent> queueEvents;

    @Inject
    UserTransaction tx;

    // ── Wiring ────────────────────────────────────────────────────────────────

    @Test
    void broadcaster_isPostgresImpl() {
        assertThat(broadcaster).isInstanceOf(PostgresWorkItemQueueEventBroadcaster.class);
    }

    // ── Happy path — end-to-end delivery ──────────────────────────────────────

    @Test
    void queueEvent_reachesStream() throws Exception {
        final List<WorkItemQueueEvent> received = new CopyOnWriteArrayList<>();
        broadcaster.stream(null).subscribe().with(received::add);

        fireCommit(UUID.randomUUID(), UUID.randomUUID(), QueueEventType.ADDED);

        Awaitility.await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(received).isNotEmpty());
    }

    @Test
    void queueEvent_containsCorrectFields() throws Exception {
        final UUID workItemId = UUID.randomUUID();
        final UUID queueId = UUID.randomUUID();
        final List<WorkItemQueueEvent> received = new CopyOnWriteArrayList<>();
        broadcaster.stream(null).subscribe().with(received::add);

        fireCommit(workItemId, queueId, QueueEventType.ADDED);

        Awaitility.await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(received)
                        .anyMatch(e -> workItemId.equals(e.workItemId())
                                && queueId.equals(e.queueViewId())
                                && QueueEventType.ADDED == e.eventType()));
    }

    @Test
    void allQueueEventTypes_deliveredCorrectly() throws Exception {
        final List<QueueEventType> types = new CopyOnWriteArrayList<>();
        broadcaster.stream(null).subscribe().with(e -> types.add(e.eventType()));

        fireCommit(UUID.randomUUID(), UUID.randomUUID(), QueueEventType.ADDED);
        fireCommit(UUID.randomUUID(), UUID.randomUUID(), QueueEventType.CHANGED);
        fireCommit(UUID.randomUUID(), UUID.randomUUID(), QueueEventType.REMOVED);

        Awaitility.await().atMost(Duration.ofSeconds(8))
                .untilAsserted(() -> assertThat(types)
                        .containsExactlyInAnyOrder(
                                QueueEventType.ADDED, QueueEventType.CHANGED, QueueEventType.REMOVED));
    }

    // ── Correctness — filter by queueViewId ───────────────────────────────────

    @Test
    void stream_filterByQueueViewId_onlyTargetDelivered() throws Exception {
        final UUID targetQueue = UUID.randomUUID();
        final List<WorkItemQueueEvent> received = new CopyOnWriteArrayList<>();
        broadcaster.stream(targetQueue).subscribe().with(received::add);

        fireCommit(UUID.randomUUID(), UUID.randomUUID(), QueueEventType.ADDED); // noise
        fireCommit(UUID.randomUUID(), targetQueue, QueueEventType.ADDED);        // target

        Awaitility.await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> {
                    assertThat(received).isNotEmpty();
                    assertThat(received).allMatch(e -> targetQueue.equals(e.queueViewId()));
                });
    }

    // ── Robustness — AFTER_SUCCESS semantics ──────────────────────────────────

    @Test
    void rolledBackTransaction_eventDoesNotReachStream() throws Exception {
        final UUID markerQueue = UUID.randomUUID();
        final List<WorkItemQueueEvent> received = new CopyOnWriteArrayList<>();
        broadcaster.stream(markerQueue).subscribe().with(received::add);

        // Fire event inside a transaction that we then roll back.
        // AFTER_SUCCESS means pg_notify fires only on commit — rollback suppresses it.
        tx.begin();
        queueEvents.fire(new WorkItemQueueEvent(
                UUID.randomUUID(), markerQueue, "rollback-test", QueueEventType.ADDED));
        tx.rollback();

        Thread.sleep(500);
        assertThat(received).isEmpty();
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private void fireCommit(final UUID workItemId, final UUID queueViewId,
            final QueueEventType type) throws Exception {
        tx.begin();
        queueEvents.fire(new WorkItemQueueEvent(workItemId, queueViewId, "test-queue", type));
        tx.commit();
    }
}
