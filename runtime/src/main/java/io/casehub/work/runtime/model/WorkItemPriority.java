package io.casehub.work.runtime.model;

/**
 * Priority level of a {@link WorkItem}, used to drive inbox ordering and
 * escalation policy thresholds. Aligns with Linear's priority vocabulary.
 */
public enum WorkItemPriority {

    /** Low priority — attend to when time permits. */
    LOW,

    /** Medium priority — standard processing order. */
    MEDIUM,

    /** High priority — handle before MEDIUM and LOW items. */
    HIGH,

    /** Urgent priority — requires immediate attention; handle before all others. */
    URGENT
}
