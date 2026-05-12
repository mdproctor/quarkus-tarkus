package io.casehub.work.queues.service;

import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import io.casehub.work.queues.event.WorkItemQueueEvent;
import io.quarkus.arc.DefaultBean;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.operators.multi.processors.BroadcastProcessor;

/**
 * Default {@link WorkItemQueueEventBroadcaster} — in-process fan-out via a Mutiny
 * {@link BroadcastProcessor}. Active unless overridden by {@code @Alternative @Priority(1)}.
 */
@ApplicationScoped
@DefaultBean
public class LocalWorkItemQueueEventBroadcaster implements WorkItemQueueEventBroadcaster {

    private final BroadcastProcessor<WorkItemQueueEvent> processor = BroadcastProcessor.create();

    /**
     * CDI observer: re-publishes every {@link WorkItemQueueEvent} to all SSE clients.
     */
    public void onEvent(@Observes final WorkItemQueueEvent event) {
        processor.onNext(event);
    }

    @Override
    public Multi<WorkItemQueueEvent> stream(final UUID queueViewId) {
        Multi<WorkItemQueueEvent> source = processor.toHotStream();
        if (queueViewId != null) {
            source = source.filter(e -> queueViewId.equals(e.queueViewId()));
        }
        return source;
    }
}
