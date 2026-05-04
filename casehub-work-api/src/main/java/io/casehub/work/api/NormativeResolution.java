package io.casehub.work.api;

/**
 * Normative resolution of a closed work item — grounded in speech act theory.
 *
 * <ul>
 *   <li>{@link #DONE} — work was fulfilled (COMMAND discharged successfully)</li>
 *   <li>{@link #DECLINE} — work was deliberately refused (won't be done)</li>
 *   <li>{@link #FAILURE} — work was attempted but could not be completed</li>
 * </ul>
 *
 * <p>Maps to {@link io.casehub.work.runtime.model.WorkItemStatus}:
 * DONE → COMPLETED, DECLINE → CANCELLED, FAILURE → REJECTED.
 *
 * <p>Used by {@link io.casehub.work.issuetracker.webhook.WebhookEvent} to translate
 * tracker-specific close vocabulary (GitHub {@code state_reason}, Jira resolution)
 * into WorkItem terminal transitions without leaking tracker terms past the provider boundary.
 */
public enum NormativeResolution {
    /** Work fulfilled — the obligation was discharged. */
    DONE,
    /** Work refused — deliberate decision not to proceed. */
    DECLINE,
    /** Work attempted but could not be completed. */
    FAILURE
}
