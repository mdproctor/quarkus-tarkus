package io.quarkiverse.work.api;

/**
 * Action to take when the completion threshold (M of N) is reached in a multi-instance group.
 */
public enum OnThresholdReached {
    /**
     * Cancel all remaining non-terminal children.
     */
    CANCEL,
    /**
     * Leave remaining children to complete naturally.
     */
    LEAVE
}
