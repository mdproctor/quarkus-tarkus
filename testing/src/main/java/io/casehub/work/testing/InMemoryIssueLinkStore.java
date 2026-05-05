package io.casehub.work.testing;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import io.casehub.work.issuetracker.model.WorkItemIssueLink;
import io.casehub.work.issuetracker.repository.IssueLinkStore;

/**
 * In-memory implementation of {@link IssueLinkStore} for use in tests of
 * applications that embed CaseHub Work with the issue-tracker module. No
 * datasource or Flyway configuration is required.
 *
 * <p>
 * Activate by including {@code casehub-work-testing} on the test classpath
 * alongside {@code casehub-work-issue-tracker}. CDI selects this bean over
 * the default JPA implementation via {@code @Alternative} and {@code @Priority(1)}.
 *
 * <p>
 * <strong>Not thread-safe</strong> — designed for single-threaded test use only.
 *
 * <p>
 * Call {@link #clear()} in a {@code @BeforeEach} method to isolate tests from
 * one another.
 */
@ApplicationScoped
@Alternative
@Priority(1)
public class InMemoryIssueLinkStore implements IssueLinkStore {

    // NOT thread-safe — designed for single-threaded test use
    private final Map<UUID, WorkItemIssueLink> store = new LinkedHashMap<>();

    /**
     * Clears all stored links. Call in {@code @BeforeEach} to isolate tests.
     */
    public void clear() {
        store.clear();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<WorkItemIssueLink> findById(final UUID id) {
        return Optional.ofNullable(store.get(id));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<WorkItemIssueLink> findByWorkItemId(final UUID workItemId) {
        return store.values().stream()
                .filter(l -> workItemId.equals(l.workItemId))
                .sorted(Comparator.comparing(l -> l.linkedAt))
                .toList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<WorkItemIssueLink> findByRef(
            final UUID workItemId, final String trackerType, final String externalRef) {
        return store.values().stream()
                .filter(l -> workItemId.equals(l.workItemId)
                        && trackerType.equals(l.trackerType)
                        && externalRef.equals(l.externalRef))
                .findFirst();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<WorkItemIssueLink> findByTrackerRef(final String trackerType, final String externalRef) {
        return store.values().stream()
                .filter(l -> trackerType.equals(l.trackerType) && externalRef.equals(l.externalRef))
                .toList();
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * If {@code link.id} is {@code null}, a fresh {@link UUID} is assigned
     * (replicating what {@code @PrePersist} does in the JPA implementation).
     * If {@code link.linkedAt} is {@code null}, it is set to {@link Instant#now()}.
     */
    @Override
    public WorkItemIssueLink save(final WorkItemIssueLink link) {
        if (link.id == null) {
            link.id = UUID.randomUUID();
        }
        if (link.linkedAt == null) {
            link.linkedAt = Instant.now();
        }
        store.put(link.id, link);
        return link;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void delete(final WorkItemIssueLink link) {
        store.remove(link.id);
    }
}
