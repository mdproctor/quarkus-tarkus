package io.quarkiverse.work.api;

/**
 * Aggregate completion status of a multi-instance WorkItem group.
 */
public enum GroupStatus {
    /**
     * Group is still accepting completions — threshold not yet reached.
     */
    IN_PROGRESS,
    /**
     * Threshold reached with majority approval — group completed successfully.
     */
    COMPLETED,
    /**
     * Threshold reached but with majority rejection or escalation — group rejected.
     */
    REJECTED
}
