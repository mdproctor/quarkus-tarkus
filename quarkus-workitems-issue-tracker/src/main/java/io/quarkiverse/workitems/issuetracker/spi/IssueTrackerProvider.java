package io.quarkiverse.workitems.issuetracker.spi;

import java.util.Optional;
import java.util.UUID;

/**
 * SPI for integrating WorkItems with an external issue tracker.
 *
 * <h2>Plugging in your own tracker</h2>
 * <p>
 * Implement this interface with {@code @ApplicationScoped @Alternative @Priority(1)}
 * to replace the default GitHub implementation, or add alongside it for a different
 * {@link #trackerType()}:
 *
 * <pre>
 * {
 *     &#64;code
 *     &#64;ApplicationScoped
 *     &#64;Alternative
 *     &#64;Priority(1)
 *     public class JiraIssueTrackerProvider implements IssueTrackerProvider {
 *         @Override
 *         public String trackerType() {
 *             return "jira";
 *         }
 *         // ...
 *     }
 * }
 * </pre>
 *
 * <p>
 * Multiple providers can coexist — the {@code IssueLinkService} routes each link
 * operation to the provider whose {@link #trackerType()} matches the link's stored type.
 *
 * <h2>externalRef format</h2>
 * <p>
 * Each provider defines its own {@code externalRef} format. The GitHub provider uses
 * {@code "owner/repo#42"}. Jira might use {@code "PROJ-1234"}. The format is opaque to
 * the rest of the system — only the provider that created the link can interpret it.
 *
 * <h2>Error handling</h2>
 * <p>
 * Methods may throw {@link IssueTrackerException} for remote errors (not-found, auth failure,
 * rate limit). The service layer translates these to appropriate HTTP responses.
 *
 * @see <a href="../docs/workitems-vs-issue-trackers.md">WorkItems vs Issue Trackers</a>
 */
public interface IssueTrackerProvider {

    /**
     * The tracker type identifier this provider handles.
     * Used to route link operations: only this provider receives calls
     * for links whose {@code trackerType} matches this value.
     *
     * <p>
     * Use lowercase, hyphen-separated names: {@code "github"}, {@code "jira"},
     * {@code "linear"}, {@code "azure-devops"}.
     *
     * @return the tracker type string; never null or blank
     */
    String trackerType();

    /**
     * Fetch the current state of an issue from the remote tracker.
     *
     * @param externalRef the tracker-specific issue reference (format defined by this provider)
     * @return a snapshot of the issue's current state
     * @throws IssueTrackerException if the issue does not exist or the remote call fails
     */
    ExternalIssueRef fetchIssue(String externalRef);

    /**
     * Create a new issue in the remote tracker and return its reference.
     *
     * <p>
     * The returned {@code Optional} is empty if issue creation is not supported
     * by this provider (e.g. a read-only integration).
     *
     * @param workItemId the UUID of the WorkItem this issue will be linked to;
     *        may be included in the issue body as a back-reference
     * @param title the issue title
     * @param body the issue body (markdown supported for most trackers)
     * @return the {@code externalRef} of the newly created issue, or empty if creation
     *         is not supported
     * @throws IssueTrackerException if the remote call fails
     */
    Optional<String> createIssue(UUID workItemId, String title, String body);

    /**
     * Close an issue in the remote tracker.
     *
     * <p>
     * Called automatically when a linked WorkItem is completed and
     * {@code quarkus.workitems.issue-tracker.github.auto-close-on-complete=true}.
     * No-op if the issue is already closed.
     *
     * @param externalRef the tracker-specific issue reference
     * @param resolution optional resolution summary to append as a comment before closing;
     *        may be null
     * @throws IssueTrackerException if the remote call fails
     */
    void closeIssue(String externalRef, String resolution);
}
