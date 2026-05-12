package io.casehub.work.queues.postgres.broadcaster;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.work.queues.event.QueueEventType;
import io.casehub.work.queues.event.WorkItemQueueEvent;

/**
 * Pure unit tests for {@link PostgresWorkItemQueueEventBroadcaster} stream/filter logic.
 *
 * <p>
 * No Quarkus, no CDI, no database. Uses {@code emit()} directly to bypass the
 * PostgreSQL pub/sub path and test only the in-process hot-stream and filter behaviour.
 *
 * <h2>Coverage</h2>
 * <ul>
 *   <li>Happy path — delivery to single and multiple subscribers
 *   <li>Correctness — filter by queueViewId, null filter receives all
 *   <li>Robustness — no subscribers (BackPressureFailure swallowed), multiple events in order
 * </ul>
 */
class PostgresQueueBroadcasterFilterTest {

    private PostgresWorkItemQueueEventBroadcaster broadcaster;

    @BeforeEach
    void setUp() {
        broadcaster = new PostgresWorkItemQueueEventBroadcaster();
    }

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    void emit_deliversEventToSubscriber() throws InterruptedException {
        final List<WorkItemQueueEvent> received = new CopyOnWriteArrayList<>();
        final CountDownLatch latch = new CountDownLatch(1);

        broadcaster.stream(null).subscribe().with(e -> {
            received.add(e);
            latch.countDown();
        });

        broadcaster.emit(event(UUID.randomUUID(), UUID.randomUUID(), QueueEventType.ADDED));

        assertThat(latch.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(received).hasSize(1);
    }

    @Test
    void emit_deliversToMultipleSubscribers() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(2);
        final List<WorkItemQueueEvent> sub1 = new CopyOnWriteArrayList<>();
        final List<WorkItemQueueEvent> sub2 = new CopyOnWriteArrayList<>();

        broadcaster.stream(null).subscribe().with(e -> { sub1.add(e); latch.countDown(); });
        broadcaster.stream(null).subscribe().with(e -> { sub2.add(e); latch.countDown(); });

        broadcaster.emit(event(UUID.randomUUID(), UUID.randomUUID(), QueueEventType.ADDED));

        assertThat(latch.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(sub1).hasSize(1);
        assertThat(sub2).hasSize(1);
    }

    @Test
    void emit_multipleEventsDeliveredInOrder() throws InterruptedException {
        final List<QueueEventType> types = new CopyOnWriteArrayList<>();
        final CountDownLatch latch = new CountDownLatch(3);
        final UUID queueId = UUID.randomUUID();

        broadcaster.stream(null).subscribe().with(e -> {
            types.add(e.eventType());
            latch.countDown();
        });

        broadcaster.emit(event(UUID.randomUUID(), queueId, QueueEventType.ADDED));
        broadcaster.emit(event(UUID.randomUUID(), queueId, QueueEventType.CHANGED));
        broadcaster.emit(event(UUID.randomUUID(), queueId, QueueEventType.REMOVED));

        assertThat(latch.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(types).containsExactly(
                QueueEventType.ADDED, QueueEventType.CHANGED, QueueEventType.REMOVED);
    }

    // ── Correctness — filter by queueViewId ───────────────────────────────────

    @Test
    void stream_filterByQueueViewId_onlyDeliversMatching() throws InterruptedException {
        final UUID targetQueue = UUID.randomUUID();
        final UUID otherQueue = UUID.randomUUID();
        final List<WorkItemQueueEvent> received = new CopyOnWriteArrayList<>();
        final CountDownLatch latch = new CountDownLatch(1);

        broadcaster.stream(targetQueue).subscribe().with(e -> {
            received.add(e);
            latch.countDown();
        });

        broadcaster.emit(event(UUID.randomUUID(), otherQueue, QueueEventType.ADDED));  // filtered
        broadcaster.emit(event(UUID.randomUUID(), targetQueue, QueueEventType.ADDED)); // passes

        assertThat(latch.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(received).hasSize(1);
        assertThat(received.get(0).queueViewId()).isEqualTo(targetQueue);
    }

    @Test
    void stream_nullQueueViewId_receivesAll() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(2);
        final List<WorkItemQueueEvent> received = new CopyOnWriteArrayList<>();

        broadcaster.stream(null).subscribe().with(e -> {
            received.add(e);
            latch.countDown();
        });

        broadcaster.emit(event(UUID.randomUUID(), UUID.randomUUID(), QueueEventType.ADDED));
        broadcaster.emit(event(UUID.randomUUID(), UUID.randomUUID(), QueueEventType.REMOVED));

        assertThat(latch.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(received).hasSize(2);
    }

    @Test
    void stream_filterPreservesAllEventTypes() throws InterruptedException {
        final UUID queueId = UUID.randomUUID();
        final List<QueueEventType> types = new CopyOnWriteArrayList<>();
        final CountDownLatch latch = new CountDownLatch(3);

        broadcaster.stream(queueId).subscribe().with(e -> {
            types.add(e.eventType());
            latch.countDown();
        });

        broadcaster.emit(event(UUID.randomUUID(), queueId, QueueEventType.ADDED));
        broadcaster.emit(event(UUID.randomUUID(), queueId, QueueEventType.CHANGED));
        broadcaster.emit(event(UUID.randomUUID(), queueId, QueueEventType.REMOVED));

        assertThat(latch.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(types).containsExactlyInAnyOrder(
                QueueEventType.ADDED, QueueEventType.CHANGED, QueueEventType.REMOVED);
    }

    // ── Robustness — no subscribers ───────────────────────────────────────────

    @Test
    void emit_withNoSubscribers_doesNotThrow() {
        broadcaster.emit(event(UUID.randomUUID(), UUID.randomUUID(), QueueEventType.ADDED));
        broadcaster.emit(event(UUID.randomUUID(), UUID.randomUUID(), QueueEventType.REMOVED));
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private WorkItemQueueEvent event(final UUID workItemId, final UUID queueViewId,
            final QueueEventType type) {
        return new WorkItemQueueEvent(workItemId, queueViewId, "test-queue", type);
    }
}
