package io.casehub.work.api;

/**
 * Canonical lifecycle event vocabulary shared across all work-management systems.
 * WorkItems, CaseHub tasks, and future work-unit types all map to these values.
 */
public enum WorkEventType {
    CREATED,
    ASSIGNED,
    STARTED,
    COMPLETED,
    REJECTED,
    DELEGATED,
    RELEASED,
    SUSPENDED,
    RESUMED,
    CANCELLED,
    EXPIRED,
    /** Claim deadline passed without the work being claimed. */
    CLAIM_EXPIRED,
    /** Child WorkItems were spawned from this work unit. */
    SPAWNED,
    ESCALATED
}
