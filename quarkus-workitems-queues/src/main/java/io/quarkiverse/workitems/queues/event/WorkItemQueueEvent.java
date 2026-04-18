package io.quarkiverse.workitems.queues.event;

import java.util.UUID;

/**
 * CDI event fired when a WorkItem's membership in a {@link io.quarkiverse.workitems.queues.model.QueueView}
 * changes.
 *
 * <p>
 * Observers use {@code @Observes WorkItemQueueEvent} to react to queue lifecycle transitions.
 * The {@link #eventType()} distinguishes whether the WorkItem entered, left, or was re-evaluated
 * within the queue.
 *
 * <p>
 * Events are fired synchronously within the same transaction as the originating WorkItem mutation.
 * If the transaction rolls back, the events are discarded.
 *
 * @param workItemId the UUID of the WorkItem whose queue membership changed
 * @param queueViewId the UUID of the affected {@link io.quarkiverse.workitems.queues.model.QueueView}
 * @param queueName human-readable name of the queue, for logging and display
 * @param eventType whether the WorkItem was added, removed, or changed within the queue
 */
public record WorkItemQueueEvent(
        UUID workItemId,
        UUID queueViewId,
        String queueName,
        QueueEventType eventType) {
}
