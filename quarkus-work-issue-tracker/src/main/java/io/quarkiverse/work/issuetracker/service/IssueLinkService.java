package io.quarkiverse.work.issuetracker.service;

import java.util.List;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import io.quarkiverse.work.issuetracker.github.GitHubIssueTrackerConfig;
import io.quarkiverse.work.issuetracker.model.WorkItemIssueLink;
import io.quarkiverse.work.issuetracker.spi.ExternalIssueRef;
import io.quarkiverse.work.issuetracker.spi.IssueTrackerException;
import io.quarkiverse.work.issuetracker.spi.IssueTrackerProvider;
import io.quarkiverse.work.runtime.event.WorkItemLifecycleEvent;
import io.quarkiverse.work.runtime.model.WorkItemStatus;

/**
 * Manages links between WorkItems and external issue tracker issues.
 *
 * <p>
 * Routes each operation to the {@link IssueTrackerProvider} whose
 * {@link IssueTrackerProvider#trackerType()} matches the link's stored type.
 * Multiple providers can coexist in the same application.
 *
 * <h2>Auto-close</h2>
 * <p>
 * Observes {@link WorkItemLifecycleEvent}: when a WorkItem is COMPLETED and
 * {@code quarkus.work.issue-tracker.github.auto-close-on-complete=true},
 * all linked issues are closed via their respective providers. Close failures
 * are logged and swallowed — they do not roll back the WorkItem completion.
 */
@ApplicationScoped
public class IssueLinkService {

    @Inject
    Instance<IssueTrackerProvider> providers;

    @Inject
    GitHubIssueTrackerConfig githubConfig;

    /**
     * Link an existing issue to a WorkItem.
     *
     * <p>
     * Fetches the issue's current title, URL, and status from the remote tracker
     * and stores them as a cached snapshot. Idempotent — if the link already exists,
     * this is a no-op.
     *
     * @param workItemId the WorkItem UUID
     * @param trackerType the tracker type (must match a registered provider)
     * @param externalRef the tracker-specific issue reference
     * @param linkedBy the actor creating this link
     * @return the created or existing link
     * @throws IssueTrackerException if the remote fetch fails
     * @throws IllegalArgumentException if no provider is registered for trackerType
     */
    @Transactional
    public WorkItemIssueLink linkExistingIssue(
            final UUID workItemId,
            final String trackerType,
            final String externalRef,
            final String linkedBy) {

        final WorkItemIssueLink existing = WorkItemIssueLink.findByRef(workItemId, trackerType, externalRef);
        if (existing != null) {
            return existing;
        }

        final IssueTrackerProvider provider = providerFor(trackerType);
        final ExternalIssueRef ref = provider.fetchIssue(externalRef);

        final WorkItemIssueLink link = new WorkItemIssueLink();
        link.workItemId = workItemId;
        link.trackerType = trackerType;
        link.externalRef = externalRef;
        link.title = ref.title();
        link.url = ref.url();
        link.status = ref.status();
        link.linkedBy = linkedBy;
        link.persist();
        return link;
    }

    /**
     * Create a new issue in the remote tracker and link it to a WorkItem.
     *
     * @param workItemId the WorkItem UUID
     * @param trackerType the tracker type
     * @param title the issue title
     * @param body the issue body (markdown)
     * @param linkedBy the actor creating the issue
     * @return the created link
     * @throws IssueTrackerException if the remote call fails or creation is not supported
     */
    @Transactional
    public WorkItemIssueLink createAndLink(
            final UUID workItemId,
            final String trackerType,
            final String title,
            final String body,
            final String linkedBy) {

        final IssueTrackerProvider provider = providerFor(trackerType);
        final String externalRef = provider.createIssue(workItemId, title, body)
                .orElseThrow(() -> new IssueTrackerException(
                        "Provider '" + trackerType + "' does not support issue creation"));

        // Fetch the newly created issue to get its URL and canonical title
        final ExternalIssueRef ref = provider.fetchIssue(externalRef);

        final WorkItemIssueLink link = new WorkItemIssueLink();
        link.workItemId = workItemId;
        link.trackerType = trackerType;
        link.externalRef = externalRef;
        link.title = ref.title();
        link.url = ref.url();
        link.status = "open";
        link.linkedBy = linkedBy;
        link.persist();
        return link;
    }

    /**
     * Return all links for a WorkItem, ordered by creation time.
     *
     * @param workItemId the WorkItem UUID
     * @return list of links; may be empty
     */
    @Transactional
    public List<WorkItemIssueLink> listLinks(final UUID workItemId) {
        return WorkItemIssueLink.findByWorkItemId(workItemId);
    }

    /**
     * Remove a specific link by ID.
     *
     * @param linkId the UUID of the link to remove
     * @param workItemId the WorkItem UUID (for ownership check)
     * @return true if deleted, false if the link was not found or belongs to a different WorkItem
     */
    @Transactional
    public boolean removeLink(final UUID linkId, final UUID workItemId) {
        final WorkItemIssueLink link = WorkItemIssueLink.findById(linkId);
        if (link == null || !link.workItemId.equals(workItemId)) {
            return false;
        }
        link.delete();
        return true;
    }

    /**
     * Refresh the status and title of all links for a WorkItem by fetching from the remote trackers.
     *
     * <p>
     * Failed fetches for individual links are logged and skipped — partial sync is preferable
     * to an all-or-nothing failure.
     *
     * @param workItemId the WorkItem UUID
     * @return the number of links successfully synced
     */
    @Transactional
    public int syncLinks(final UUID workItemId) {
        final List<WorkItemIssueLink> links = WorkItemIssueLink.findByWorkItemId(workItemId);
        int synced = 0;
        for (final WorkItemIssueLink link : links) {
            try {
                final IssueTrackerProvider provider = providerFor(link.trackerType);
                final ExternalIssueRef ref = provider.fetchIssue(link.externalRef);
                link.title = ref.title();
                link.url = ref.url();
                link.status = ref.status();
                synced++;
            } catch (Exception e) {
                // log and continue; partial sync is acceptable
                org.jboss.logging.Logger.getLogger(IssueLinkService.class)
                        .warnf("Sync failed for %s:%s — %s", link.trackerType, link.externalRef, e.getMessage());
            }
        }
        return synced;
    }

    /**
     * CDI observer: closes linked issues when a WorkItem is COMPLETED and
     * {@code auto-close-on-complete} is enabled.
     *
     * <p>
     * Close failures are logged and swallowed — they must not roll back the WorkItem
     * completion transaction.
     */
    @Transactional
    public void onLifecycleEvent(@Observes final WorkItemLifecycleEvent event) {
        if (event.status() != WorkItemStatus.COMPLETED) {
            return;
        }
        if (!githubConfig.autoCloseOnComplete()) {
            return;
        }

        final List<WorkItemIssueLink> links = WorkItemIssueLink.findByWorkItemId(event.workItemId());
        for (final WorkItemIssueLink link : links) {
            if ("closed".equals(link.status)) {
                continue; // already closed
            }
            try {
                final IssueTrackerProvider provider = providerFor(link.trackerType);
                final String resolution = event.detail() != null
                        ? "WorkItem completed. " + event.detail()
                        : "WorkItem completed.";
                provider.closeIssue(link.externalRef, resolution);
                link.status = "closed";
            } catch (Exception e) {
                org.jboss.logging.Logger.getLogger(IssueLinkService.class)
                        .warnf("Auto-close failed for %s:%s — %s",
                                link.trackerType, link.externalRef, e.getMessage());
            }
        }
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private IssueTrackerProvider providerFor(final String trackerType) {
        for (final IssueTrackerProvider provider : providers) {
            if (trackerType.equals(provider.trackerType())) {
                return provider;
            }
        }
        throw new IllegalArgumentException(
                "No IssueTrackerProvider registered for type '" + trackerType +
                        "'. Available: " + availableTypes());
    }

    private List<String> availableTypes() {
        return providers.stream().map(IssueTrackerProvider::trackerType).toList();
    }
}
