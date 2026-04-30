package io.casehub.work.examples.queues.lifecycle;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import io.casehub.work.queues.event.QueueEventType;
import io.casehub.work.queues.event.WorkItemQueueEvent;

/**
 * CDI observer that records {@link WorkItemQueueEvent} instances for the queue lifecycle example.
 *
 * <p>
 * Scoped to {@link ApplicationScoped} so it survives across the scenario's step-by-step
 * REST calls. The scenario calls {@link #drain()} at the end to collect all observed events.
 *
 * <p>
 * <strong>Note:</strong> this is a demo logger for the lifecycle scenario, not a production
 * pattern. In a real application you would define a purpose-specific observer that reacts
 * to queue events — for example, sending a notification or updating a dashboard.
 *
 * @param workItemId the WorkItem whose queue membership changed
 * @param queueViewId the affected queue
 * @param queueName the queue's display name
 * @param eventType ADDED, REMOVED, or CHANGED
 */
@ApplicationScoped
public class QueueEventLog {

    /**
     * A single queue lifecycle event captured during the scenario run.
     *
     * @param workItemId the WorkItem whose queue membership changed
     * @param queueViewId the UUID of the affected queue
     * @param queueName human-readable queue name
     * @param eventType ADDED, REMOVED, or CHANGED
     */
    public record Entry(UUID workItemId, UUID queueViewId, String queueName, QueueEventType eventType) {
    }

    private final CopyOnWriteArrayList<Entry> entries = new CopyOnWriteArrayList<>();

    /**
     * Observes every {@link WorkItemQueueEvent} fired in the application and appends an
     * {@link Entry} to the log. Fired synchronously within the WorkItem mutation's transaction.
     *
     * @param event the queue lifecycle event
     */
    public void onQueueEvent(@Observes final WorkItemQueueEvent event) {
        entries.add(new Entry(event.workItemId(), event.queueViewId(), event.queueName(), event.eventType()));
    }

    /**
     * Returns all captured events since the last {@link #clear()} and removes them from the log.
     *
     * @return snapshot of captured events; may be empty
     */
    public List<Entry> drain() {
        final List<Entry> snapshot = List.copyOf(entries);
        entries.clear();
        return snapshot;
    }

    /** Remove all captured events without returning them. */
    public void clear() {
        entries.clear();
    }
}
