package io.quarkiverse.workitems.issuetracker.spi;

/**
 * Snapshot of an issue's state as returned by an {@link IssueTrackerProvider}.
 *
 * <p>
 * This is a point-in-time read — the values reflect the remote state at the moment
 * of the fetch and are not kept in sync automatically.
 *
 * @param trackerType the tracker that owns this issue (e.g. {@code "github"})
 * @param externalRef the tracker-specific reference (e.g. {@code "owner/repo#42"})
 * @param title the issue's current title in the remote system
 * @param url the direct URL to the issue (for display and linking)
 * @param status normalised status: {@code "open"}, {@code "closed"}, or {@code "unknown"}
 */
public record ExternalIssueRef(
        String trackerType,
        String externalRef,
        String title,
        String url,
        String status) {
}
