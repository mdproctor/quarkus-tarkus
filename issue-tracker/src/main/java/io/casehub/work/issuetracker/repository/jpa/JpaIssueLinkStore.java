package io.casehub.work.issuetracker.repository.jpa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;

import io.casehub.work.issuetracker.model.WorkItemIssueLink;
import io.casehub.work.issuetracker.repository.IssueLinkStore;

/**
 * Default JPA implementation of {@link IssueLinkStore} backed by Hibernate ORM
 * with Panache. Delegates to the static and instance methods on
 * {@link WorkItemIssueLink}.
 *
 * <p>
 * No query logic lives here — this is a thin adapter. All callers must ensure
 * an active JTA transaction exists before calling mutating methods.
 */
@ApplicationScoped
public class JpaIssueLinkStore implements IssueLinkStore {

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<WorkItemIssueLink> findById(final UUID id) {
        return Optional.ofNullable(WorkItemIssueLink.findById(id));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<WorkItemIssueLink> findByWorkItemId(final UUID workItemId) {
        return WorkItemIssueLink.findByWorkItemId(workItemId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<WorkItemIssueLink> findByRef(
            final UUID workItemId, final String trackerType, final String externalRef) {
        return Optional.ofNullable(WorkItemIssueLink.findByRef(workItemId, trackerType, externalRef));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<WorkItemIssueLink> findByTrackerRef(final String trackerType, final String externalRef) {
        return WorkItemIssueLink.findByTrackerRef(trackerType, externalRef);
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * Calls {@link WorkItemIssueLink#persist()} which is a no-op for already-managed
     * entities; dirty-checking at transaction commit handles those updates.
     */
    @Override
    public WorkItemIssueLink save(final WorkItemIssueLink link) {
        link.persist();
        return link;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void delete(final WorkItemIssueLink link) {
        link.delete();
    }
}
