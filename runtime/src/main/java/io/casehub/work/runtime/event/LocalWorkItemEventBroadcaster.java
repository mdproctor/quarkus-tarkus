package io.casehub.work.runtime.event;

import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import io.quarkus.arc.DefaultBean;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.operators.multi.processors.BroadcastProcessor;
import io.smallrye.mutiny.subscription.BackPressureFailure;

/**
 * Default {@link WorkItemEventBroadcaster} — in-process fan-out via a Mutiny
 * {@link BroadcastProcessor}. Active in any application that does not provide an
 * alternative broadcaster via {@code @Alternative @Priority(1)}.
 *
 * <h2>Hot stream semantics</h2>
 * <p>
 * The {@code BroadcastProcessor} is a <em>hot</em> stream — it does not replay past events
 * to new subscribers. Clients receive only events that occur while connected.
 *
 * <h2>Thread safety</h2>
 * <p>
 * {@code BroadcastProcessor} is thread-safe. CDI event delivery and SSE subscriber
 * callbacks may run on different threads without additional synchronisation.
 */
@ApplicationScoped
@DefaultBean
public class LocalWorkItemEventBroadcaster implements WorkItemEventBroadcaster {

    private final BroadcastProcessor<WorkItemLifecycleEvent> processor = BroadcastProcessor.create();

    /**
     * CDI observer: called synchronously on every WorkItem lifecycle transition.
     * Re-publishes the event onto the hot stream for all connected SSE clients.
     */
    public void onEvent(@Observes final WorkItemLifecycleEvent event) {
        try {
            processor.onNext(event);
        } catch (BackPressureFailure ignored) {
            // No SSE subscribers connected — hot stream drops events with no listener.
        }
    }

    @Override
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
