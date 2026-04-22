package io.quarkiverse.work.core.filter;

/** Lifecycle events that filter rules can subscribe to. */
public enum FilterEvent {
    /** WorkItem first persisted (CREATED audit event). */
    ADD,
    /** WorkItem status changed but not yet terminal. */
    UPDATE,
    /** WorkItem reached a terminal state (COMPLETED, REJECTED, CANCELLED, EXPIRED, ESCALATED). */
    REMOVE
}
