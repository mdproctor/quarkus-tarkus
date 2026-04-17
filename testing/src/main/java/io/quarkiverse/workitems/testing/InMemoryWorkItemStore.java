package io.quarkiverse.workitems.testing;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import io.quarkiverse.workitems.runtime.model.WorkItem;
import io.quarkiverse.workitems.runtime.repository.WorkItemQuery;
import io.quarkiverse.workitems.runtime.repository.WorkItemStore;

/**
 * In-memory implementation of {@link WorkItemStore} for use in tests of
 * applications that embed Quarkus WorkItems. No datasource or Flyway configuration
 * is required.
 *
 * <p>
 * Activate by including {@code quarkus-workitems-testing} on the test classpath. CDI
 * selects this bean over the default Panache implementation via {@code @Alternative}
 * and {@code @Priority(1)}.
 *
 * <p>
 * <strong>Not thread-safe</strong> — designed for single-threaded test use only.
 *
 * <p>
 * Call {@link #clear()} in a {@code @BeforeEach} method to isolate tests from one
 * another.
 */
@ApplicationScoped
@Alternative
@Priority(1)
public class InMemoryWorkItemStore implements WorkItemStore {

    // NOT thread-safe — designed for single-threaded test use
    private final Map<UUID, WorkItem> store = new LinkedHashMap<>();

    /**
     * Clears all stored WorkItems. Call in {@code @BeforeEach} to isolate tests.
     */
    public void clear() {
        store.clear();
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * If {@code workItem.id} is {@code null} a fresh {@link UUID} is assigned before
     * the item is stored.
     */
    @Override
    public WorkItem put(final WorkItem workItem) {
        if (workItem.id == null) {
            workItem.id = UUID.randomUUID();
        }
        store.put(workItem.id, workItem);
        return workItem;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<WorkItem> get(final UUID id) {
        return Optional.ofNullable(store.get(id));
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * Assignment visibility is evaluated with OR logic across the three assignment
     * dimensions: {@code assigneeId}, {@code candidateGroups}, and
     * {@code candidateUsers}. Each additional filter (status, priority, category,
     * followUpBefore, etc.) is then applied with AND logic.
     */
    @Override
    public List<WorkItem> scan(final WorkItemQuery query) {
        return store.values().stream()
                .filter(wi -> matchesAssignment(wi, query))
                .filter(wi -> matchesFilters(wi, query))
                .toList();
    }

    /**
     * Returns a mutable copy of all stored items, for convenience in tests.
     *
     * @return list of all work items
     */
    public List<WorkItem> findAll() {
        return new ArrayList<>(store.values());
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} if the work item is visible under the assignment criteria
     * in the query, using OR logic. If no assignment criteria are set, all items match.
     */
    private boolean matchesAssignment(final WorkItem wi, final WorkItemQuery q) {
        // If no assignment criteria — no assignment constraint
        if (q.assigneeId() == null && (q.candidateGroups() == null || q.candidateGroups().isEmpty())
                && q.candidateUserId() == null) {
            return true;
        }
        // OR logic across assignment dimensions
        if (q.assigneeId() != null && q.assigneeId().equals(wi.assigneeId)) {
            return true;
        }
        if (q.candidateUserId() != null && q.candidateUserId().equals(wi.assigneeId)) {
            return true;
        }
        if (q.assigneeId() != null && wi.candidateUsers != null && containsToken(wi.candidateUsers, q.assigneeId())) {
            return true;
        }
        if (q.candidateUserId() != null && wi.candidateUsers != null
                && containsToken(wi.candidateUsers, q.candidateUserId())) {
            return true;
        }
        if (q.candidateGroups() != null && wi.candidateGroups != null) {
            for (final String g : q.candidateGroups()) {
                if (containsToken(wi.candidateGroups, g)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns {@code true} if the work item passes all non-null AND filters in the query.
     */
    private boolean matchesFilters(final WorkItem wi, final WorkItemQuery q) {
        if (q.status() != null && wi.status != q.status()) {
            return false;
        }
        if (q.statusIn() != null && !q.statusIn().contains(wi.status)) {
            return false;
        }
        if (q.priority() != null && wi.priority != q.priority()) {
            return false;
        }
        if (q.category() != null && !q.category().equals(wi.category)) {
            return false;
        }
        if (q.followUpBefore() != null && (wi.followUpDate == null || wi.followUpDate.isAfter(q.followUpBefore()))) {
            return false;
        }
        if (q.expiresAtOrBefore() != null && (wi.expiresAt == null || wi.expiresAt.isAfter(q.expiresAtOrBefore()))) {
            return false;
        }
        if (q.claimDeadlineOrBefore() != null
                && (wi.claimDeadline == null || wi.claimDeadline.isAfter(q.claimDeadlineOrBefore()))) {
            return false;
        }
        if (q.labelPattern() != null) {
            final boolean matchesLabel = wi.labels != null && wi.labels.stream()
                    .anyMatch(l -> io.quarkiverse.workitems.runtime.service.LabelVocabularyService
                            .matchesPattern(q.labelPattern(), l.path));
            if (!matchesLabel) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns {@code true} if {@code csv} contains {@code token} as an exact
     * comma-separated element (after trimming whitespace). Avoids substring
     * false-positives — e.g. {@code "bob"} does NOT match {@code "bobby"}.
     *
     * @param csv comma-separated string to search
     * @param token the exact token to look for
     * @return {@code true} if the token appears as a discrete element
     */
    private boolean containsToken(final String csv, final String token) {
        for (final String element : csv.split(",")) {
            if (element.trim().equals(token)) {
                return true;
            }
        }
        return false;
    }
}
