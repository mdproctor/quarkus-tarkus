package io.casehub.work.runtime.event;

import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.operators.multi.processors.BroadcastProcessor;

/**
 * Application-scoped broadcaster that bridges CDI {@link WorkItemLifecycleEvent} to
 * a reactive hot stream for Server-Sent Events consumers.
 *
 * <h2>Why this exists</h2>
 * <p>
 * CDI events are synchronous and deliver only to active observers — there is no
 * built-in mechanism to fan out to multiple HTTP clients. This broadcaster fills that gap:
 * it observes every {@link WorkItemLifecycleEvent} and re-publishes it onto a
 * {@link BroadcastProcessor}, which any number of SSE consumers can subscribe to.
 *
 * <h2>Hot stream semantics</h2>
 * <p>
 * The underlying {@code BroadcastProcessor} is a <em>hot</em> stream — it does not
 * replay past events to new subscribers. A client that connects after an event fires
 * will not receive that event. This is the correct behaviour for a live notification
 * channel: clients receive only events that occur while they are connected.
 *
 * <h2>Filtering</h2>
 * <p>
 * {@link #stream(UUID, String)} accepts optional filters that are applied per-subscriber,
 * not at the broadcast level. All events still flow through the single broadcaster;
 * each subscriber sees only what it requested.
 *
 * <h2>Thread safety</h2>
 * <p>
 * {@code BroadcastProcessor} is thread-safe. CDI event delivery and SSE subscriber
 * callbacks may run on different threads without additional synchronisation.
 */
@ApplicationScoped
public class WorkItemEventBroadcaster {

    private final BroadcastProcessor<WorkItemLifecycleEvent> processor = BroadcastProcessor.create();

    /**
     * CDI observer: called synchronously on every WorkItem lifecycle transition.
     * Re-publishes the event onto the hot stream for all connected SSE clients.
     *
     * @param event the lifecycle event fired by {@link io.casehub.work.runtime.service.WorkItemService}
     */
    public void onEvent(@Observes final WorkItemLifecycleEvent event) {
        processor.onNext(event);
    }

    /**
     * Returns a hot {@link Multi} of lifecycle events, optionally filtered.
     *
     * <p>
     * Subscribers receive only events that occur after they subscribe — past events
     * are not replayed. This is intentional: SSE consumers are real-time notification
     * channels, not event logs. Use {@code GET /workitems/{id}} to fetch current state.
     *
     * @param workItemId if non-null, only events for this WorkItem are emitted
     * @param type if non-null, only events whose type suffix matches (case-insensitive)
     *        are emitted — e.g. {@code "created"}, {@code "completed"}
     * @return a hot {@link Multi} delivering matching {@link WorkItemLifecycleEvent} instances
     */
    public Multi<WorkItemLifecycleEvent> stream(final UUID workItemId, final String type) {
        Multi<WorkItemLifecycleEvent> source = processor.toHotStream();

        if (workItemId != null) {
            source = source.filter(e -> workItemId.equals(e.workItemId()));
        }

        if (type != null && !type.isBlank()) {
            final String suffix = type.toLowerCase();
            source = source.filter(e -> e.type() != null && e.type().toLowerCase().endsWith(suffix));
        }

        return source;
    }
}
