package io.casehub.work.issuetracker.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.casehub.work.issuetracker.model.WorkItemIssueLink;

/**
 * Store SPI for {@link WorkItemIssueLink} persistence.
 *
 * <p>
 * Replaces direct Panache static calls in
 * {@link io.casehub.work.issuetracker.webhook.WebhookEventHandler} and
 * {@link io.casehub.work.issuetracker.service.IssueLinkService} with an
 * injectable seam, enabling full unit testing without CDI or a database.
 *
 * <p>
 * The default implementation ({@link jpa.JpaIssueLinkStore}) uses Hibernate ORM
 * with Panache. The in-memory alternative is provided in
 * {@code casehub-work-testing} for application-level tests.
 *
 * <p>
 * Custom implementations register as {@code @ApplicationScoped @Alternative
 * @Priority(1)} CDI beans.
 */
public interface IssueLinkStore {

    /**
     * Find a link by its surrogate primary key.
     *
     * @param id the link UUID
     * @return the link, or empty if not found
     */
    Optional<WorkItemIssueLink> findById(UUID id);

    /**
     * Return all links for the given WorkItem, ordered by creation time ascending.
     *
     * @param workItemId the WorkItem UUID
     * @return list of links; may be empty, never null
     */
    List<WorkItemIssueLink> findByWorkItemId(UUID workItemId);

    /**
     * Find a specific link by WorkItem, tracker type, and external reference.
     *
     * @param workItemId the WorkItem UUID
     * @param trackerType the tracker type string (e.g. {@code "github"})
     * @param externalRef the tracker-specific reference (e.g. {@code "owner/repo#42"})
     * @return the link, or empty if not found
     */
    Optional<WorkItemIssueLink> findByRef(UUID workItemId, String trackerType, String externalRef);

    /**
     * Return all links for the given tracker type and external reference,
     * across all WorkItems. Used by webhook handlers that receive the tracker ref
     * but not the WorkItem ID.
     *
     * @param trackerType the tracker type string
     * @param externalRef the tracker-specific reference
     * @return list of links; may be empty, never null
     */
    List<WorkItemIssueLink> findByTrackerRef(String trackerType, String externalRef);

    /**
     * Persist or update a link.
     *
     * <p>
     * For new links (id is null), the entity's {@code @PrePersist} hook assigns a
     * UUID and {@code linkedAt} timestamp. For existing managed entities in a
     * JPA context, dirty-checking at transaction commit handles those updates — an
     * explicit {@code save()} call makes the intent clear.
     *
     * @param link the link to persist; must not be null
     * @return the saved link
     */
    WorkItemIssueLink save(WorkItemIssueLink link);

    /**
     * Delete a link from the store.
     *
     * @param link the link to delete; must not be null
     */
    void delete(WorkItemIssueLink link);
}
