package io.casehub.work.postgres.broadcaster;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.work.runtime.event.WorkItemLifecycleEvent;
import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.runtime.model.WorkItemStatus;

/**
 * Pure unit tests for {@link PostgresWorkItemEventBroadcaster} stream/filter logic.
 *
 * <p>
 * No Quarkus, no CDI, no database. Uses {@code emit()} directly to bypass the
 * PostgreSQL pub/sub path and test only the in-process hot-stream and filter behaviour.
 * This mirrors the pattern in {@code WorkItemEventBroadcasterTest} for the local impl.
 *
 * <h2>Coverage</h2>
 * <ul>
 *   <li>Happy path — delivery to single and multiple subscribers
 *   <li>Correctness — filter by workItemId, filter by type suffix, combined filter
 *   <li>Robustness — no subscribers, BackPressureFailure swallowed silently
 * </ul>
 */
class PostgresWorkItemEventBroadcasterFilterTest {

    private PostgresWorkItemEventBroadcaster broadcaster;

    @BeforeEach
    void setUp() {
        broadcaster = new PostgresWorkItemEventBroadcaster();
    }

    // ── Happy path — basic delivery ───────────────────────────────────────────

    @Test
    void emit_deliversEventToSubscriber() throws InterruptedException {
        final List<WorkItemLifecycleEvent> received = new CopyOnWriteArrayList<>();
        final CountDownLatch latch = new CountDownLatch(1);

        broadcaster.stream(null, null).subscribe().with(e -> {
            received.add(e);
            latch.countDown();
        });

        broadcaster.emit(event("CREATED", UUID.randomUUID()));

        assertThat(latch.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(received).hasSize(1);
    }

    @Test
    void emit_deliversToMultipleSubscribers() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(2);
        final List<WorkItemLifecycleEvent> sub1 = new CopyOnWriteArrayList<>();
        final List<WorkItemLifecycleEvent> sub2 = new CopyOnWriteArrayList<>();

        broadcaster.stream(null, null).subscribe().with(e -> { sub1.add(e); latch.countDown(); });
        broadcaster.stream(null, null).subscribe().with(e -> { sub2.add(e); latch.countDown(); });

        broadcaster.emit(event("ASSIGNED", UUID.randomUUID()));

        assertThat(latch.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(sub1).hasSize(1);
        assertThat(sub2).hasSize(1);
    }

    @Test
    void emit_multipleEventsDeliveredInOrder() throws InterruptedException {
        final UUID id = UUID.randomUUID();
        final List<String> types = new CopyOnWriteArrayList<>();
        final CountDownLatch latch = new CountDownLatch(3);

        broadcaster.stream(null, null).subscribe().with(e -> {
            types.add(e.type());
            latch.countDown();
        });

        broadcaster.emit(event("CREATED", id));
        broadcaster.emit(event("ASSIGNED", id));
        broadcaster.emit(event("COMPLETED", id));

        assertThat(latch.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(types).extracting(t -> t.substring(t.lastIndexOf('.') + 1))
                .containsExactly("created", "assigned", "completed");
    }

    // ── Correctness — filter by workItemId ────────────────────────────────────

    @Test
    void stream_filterByWorkItemId_onlyDeliversMatching() throws InterruptedException {
        final UUID target = UUID.randomUUID();
        final UUID other = UUID.randomUUID();
        final List<WorkItemLifecycleEvent> received = new CopyOnWriteArrayList<>();
        final CountDownLatch latch = new CountDownLatch(1);

        broadcaster.stream(target, null).subscribe().with(e -> {
            received.add(e);
            latch.countDown();
        });

        broadcaster.emit(event("CREATED", other));  // filtered
        broadcaster.emit(event("CREATED", target)); // passes

        assertThat(latch.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(received).hasSize(1);
        assertThat(received.get(0).workItemId()).isEqualTo(target);
    }

    @Test
    void stream_nullWorkItemId_receivesAll() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(2);
        final List<WorkItemLifecycleEvent> received = new CopyOnWriteArrayList<>();

        broadcaster.stream(null, null).subscribe().with(e -> {
            received.add(e);
            latch.countDown();
        });

        broadcaster.emit(event("CREATED", UUID.randomUUID()));
        broadcaster.emit(event("CREATED", UUID.randomUUID()));

        assertThat(latch.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(received).hasSize(2);
    }

    // ── Correctness — filter by type ──────────────────────────────────────────

    @Test
    void stream_filterByType_onlyDeliversMatching() throws InterruptedException {
        final List<WorkItemLifecycleEvent> received = new CopyOnWriteArrayList<>();
        final CountDownLatch latch = new CountDownLatch(1);

        broadcaster.stream(null, "completed").subscribe().with(e -> {
            received.add(e);
            latch.countDown();
        });

        broadcaster.emit(event("CREATED", UUID.randomUUID()));  // filtered
        broadcaster.emit(event("ASSIGNED", UUID.randomUUID())); // filtered
        broadcaster.emit(event("COMPLETED", UUID.randomUUID())); // passes

        assertThat(latch.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(received).hasSize(1);
        assertThat(received.get(0).type()).endsWith("completed");
    }

    @Test
    void stream_typeFilter_isCaseInsensitive() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        final List<WorkItemLifecycleEvent> received = new CopyOnWriteArrayList<>();

        broadcaster.stream(null, "CREATED").subscribe().with(e -> {
            received.add(e);
            latch.countDown();
        });
        broadcaster.emit(event("CREATED", UUID.randomUUID()));

        assertThat(latch.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(received).hasSize(1);
    }

    // ── Correctness — combined filter ─────────────────────────────────────────

    @Test
    void stream_combinedFilter_workItemIdAndType() throws InterruptedException {
        final UUID target = UUID.randomUUID();
        final CountDownLatch latch = new CountDownLatch(1);
        final List<WorkItemLifecycleEvent> received = new CopyOnWriteArrayList<>();

        broadcaster.stream(target, "completed").subscribe().with(e -> {
            received.add(e);
            latch.countDown();
        });

        broadcaster.emit(event("CREATED", target));          // wrong type
        broadcaster.emit(event("COMPLETED", UUID.randomUUID())); // wrong id
        broadcaster.emit(event("COMPLETED", target));        // matches both

        assertThat(latch.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(received).hasSize(1);
        assertThat(received.get(0).workItemId()).isEqualTo(target);
    }

    // ── Robustness — no subscribers ───────────────────────────────────────────

    @Test
    void emit_withNoSubscribers_doesNotThrow() {
        // BackPressureFailure must be swallowed silently — no exception propagated
        broadcaster.emit(event("CREATED", UUID.randomUUID()));
        broadcaster.emit(event("CREATED", UUID.randomUUID()));
        // If we reach this point, BackPressureFailure was handled correctly
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private WorkItemLifecycleEvent event(final String name, final UUID workItemId) {
        final WorkItem wi = new WorkItem();
        wi.id = workItemId;
        wi.status = WorkItemStatus.PENDING;
        return WorkItemLifecycleEvent.of(name, wi, "test", null);
    }
}
