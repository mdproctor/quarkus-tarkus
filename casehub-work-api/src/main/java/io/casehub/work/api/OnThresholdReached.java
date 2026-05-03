package io.casehub.work.api;

/**
 * Action to take on remaining non-terminal children when the M-of-N completion
 * threshold is reached in a multi-instance group.
 *
 * <h2>Choosing a value</h2>
 * <ul>
 *   <li><strong>{@link #KEEP}</strong> — the default and the safe choice. No side effects.
 *       Children that have not yet completed are left active. Use this when every child's
 *       outcome matters for audit, reporting, or business reasons even after the threshold
 *       is met (e.g. regulatory checks where all results must be recorded).</li>
 *   <li><strong>{@link #SUSPEND}</strong> — pause active children (ASSIGNED or IN_PROGRESS).
 *       Signals that their work is on hold while the group outcome is processed. PENDING
 *       children are left unclaimed. Use this when active work should not be abandoned
 *       but also should not continue until the group-level decision is reviewed.</li>
 *   <li><strong>{@link #CANCEL}</strong> — opt-in only. Cancels all remaining non-terminal
 *       children immediately. Use this when surplus work is definitively unwanted once the
 *       threshold is met (e.g. first-N-responders where late completions have no value).
 *       Callers must set this explicitly — it is never applied by default.</li>
 * </ul>
 *
 * <p>
 * When {@code onThresholdReached} is not set on a template, the group behaves as
 * {@link #KEEP} — no children are modified after the threshold fires.
 */
public enum OnThresholdReached {

    /**
     * Leave remaining children active to complete naturally.
     *
     * <p>
     * This is the default behaviour when {@code onThresholdReached} is not explicitly set.
     * No side effects on non-terminal children — every child's outcome is independently
     * recorded regardless of whether the group threshold has already been met.
     */
    KEEP,

    /**
     * Suspend active children (ASSIGNED or IN_PROGRESS) when the threshold is reached.
     *
     * <p>
     * Signals that in-progress work is on hold while the group-level outcome is processed.
     * PENDING children (not yet claimed) are left unchanged — suspending unclaimed work
     * is not meaningful.
     */
    SUSPEND,

    /**
     * Cancel all remaining non-terminal children when the threshold is reached.
     *
     * <p>
     * <strong>Opt-in only</strong> — must be set explicitly. Never applied by default.
     * Use when surplus work has no value after the threshold fires, such as first-N-responder
     * patterns where late completions are irrelevant.
     */
    CANCEL
}
