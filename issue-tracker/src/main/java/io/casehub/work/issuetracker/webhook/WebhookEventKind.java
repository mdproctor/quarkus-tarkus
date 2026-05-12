package io.casehub.work.issuetracker.webhook;

/** The kind of inbound tracker event, normalised across GitHub and Jira. */
public enum WebhookEventKind {
    /** Issue closed — check {@link WebhookEvent#normativeResolution()} for DONE/DECLINE/FAILURE. */
    CLOSED,
    /** An actor was assigned to the issue. */
    ASSIGNED,
    /** The issue was unassigned. */
    UNASSIGNED,
    /** The issue title (GitHub) or summary (Jira) changed. */
    TITLE_CHANGED,
    /** The issue body (GitHub) or description (Jira) changed. */
    DESCRIPTION_CHANGED,
    /** The issue priority changed. */
    PRIORITY_CHANGED,
    /** A non-managed label was added. */
    LABEL_ADDED,
    /** A non-managed label was removed. */
    LABEL_REMOVED
}
