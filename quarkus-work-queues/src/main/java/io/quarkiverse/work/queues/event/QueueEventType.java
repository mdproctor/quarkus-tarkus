package io.quarkiverse.work.queues.event;

/**
 * Type of a {@link WorkItemQueueEvent} — describes how a WorkItem's queue membership changed.
 */
public enum QueueEventType {

    /** WorkItem entered a queue it was not previously a member of. */
    ADDED,

    /**
     * WorkItem left a queue and was not re-added within the same operation.
     * Distinguishable from an intermediate label strip during filter re-evaluation,
     * which produces {@link #CHANGED} rather than REMOVED + ADDED.
     */
    REMOVED,

    /**
     * WorkItem was in the queue before and after the current operation.
     * Fired when the filter engine re-evaluates the item (strips INFERRED labels,
     * then re-applies them) and the item remains a member of the queue.
     * Signals that the WorkItem's state may have changed even if queue membership did not.
     */
    CHANGED
}
