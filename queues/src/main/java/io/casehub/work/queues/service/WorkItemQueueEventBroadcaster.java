package io.casehub.work.queues.service;

import java.util.UUID;

import io.casehub.work.queues.event.WorkItemQueueEvent;
import io.smallrye.mutiny.Multi;

/**
 * SPI: fan-out WorkItem queue events (ADDED, REMOVED, CHANGED) to SSE subscribers.
 *
 * <p>
 * The default implementation ({@link LocalWorkItemQueueEventBroadcaster}) uses an
 * in-process Mutiny {@code BroadcastProcessor}. Alternative backends can replace it
 * via {@code @Alternative @Priority(1)}.
 *
 * <p>
 * Mirrors the contract of {@code WorkItemEventBroadcaster} in the core runtime module.
 */
public interface WorkItemQueueEventBroadcaster {

    /**
     * Returns a hot stream of queue events, optionally filtered to a single queue.
     *
     * @param queueViewId if non-null, only events for this queue are emitted
     * @return hot {@link Multi} of matching {@link WorkItemQueueEvent} instances
     */
    Multi<WorkItemQueueEvent> stream(UUID queueViewId);
}
