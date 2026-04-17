package io.quarkiverse.workitems.runtime.repository.jpa;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.workitems.runtime.model.WorkItem;
import io.quarkiverse.workitems.runtime.repository.WorkItemQuery;
import io.quarkiverse.workitems.runtime.repository.WorkItemStore;

/**
 * Default JPA/Panache implementation of {@link WorkItemStore}.
 *
 * <p>
 * The {@link #scan} method builds a dynamic JPQL query from the non-null fields of
 * the supplied {@link WorkItemQuery}, replacing the five separate query methods of
 * the former {@code JpaWorkItemRepository}.
 */
@ApplicationScoped
public class JpaWorkItemStore implements WorkItemStore {

    @Override
    public WorkItem put(final WorkItem workItem) {
        workItem.persistAndFlush();
        return workItem;
    }

    @Override
    public Optional<WorkItem> get(final UUID id) {
        return Optional.ofNullable(WorkItem.findById(id));
    }

    @Override
    public List<WorkItem> scan(final WorkItemQuery query) {
        final Map<String, Object> params = new HashMap<>();
        final StringBuilder jpql = new StringBuilder();

        // ── Assignment — OR logic ────────────────────────────────────────────
        final boolean hasAssigneeId = query.assigneeId() != null;
        final boolean hasCandidateGroups = query.candidateGroups() != null && !query.candidateGroups().isEmpty();
        final boolean hasCandidateUserId = query.candidateUserId() != null;
        final boolean hasAssignment = hasAssigneeId || hasCandidateGroups || hasCandidateUserId;

        if (hasAssignment) {
            jpql.append("(1=0");
            if (hasAssigneeId) {
                jpql.append(" OR assigneeId = :assigneeId OR candidateUsers LIKE :assigneeIdLike");
                params.put("assigneeId", query.assigneeId());
                params.put("assigneeIdLike", "%" + query.assigneeId() + "%");
            }
            if (hasCandidateGroups) {
                for (int i = 0; i < query.candidateGroups().size(); i++) {
                    final String key = "group" + i;
                    jpql.append(" OR candidateGroups LIKE :").append(key);
                    params.put(key, "%" + query.candidateGroups().get(i) + "%");
                }
            }
            if (hasCandidateUserId && !hasAssigneeId) {
                // candidateUserId provided without assigneeId — match via candidateUsers LIKE
                jpql.append(" OR candidateUsers LIKE :candidateUserIdLike");
                params.put("candidateUserIdLike", "%" + query.candidateUserId() + "%");
            }
            jpql.append(")");
        }

        // ── Filters — AND logic ──────────────────────────────────────────────
        if (query.status() != null) {
            if (jpql.length() > 0) {
                jpql.append(" AND ");
            }
            jpql.append("status = :status");
            params.put("status", query.status());
        }

        if (query.statusIn() != null && !query.statusIn().isEmpty()) {
            if (jpql.length() > 0) {
                jpql.append(" AND ");
            }
            jpql.append("status IN (:statusIn)");
            params.put("statusIn", query.statusIn());
        }

        if (query.priority() != null) {
            if (jpql.length() > 0) {
                jpql.append(" AND ");
            }
            jpql.append("priority = :priority");
            params.put("priority", query.priority());
        }

        if (query.category() != null) {
            if (jpql.length() > 0) {
                jpql.append(" AND ");
            }
            jpql.append("category = :category");
            params.put("category", query.category());
        }

        if (query.followUpBefore() != null) {
            if (jpql.length() > 0) {
                jpql.append(" AND ");
            }
            jpql.append("followUpDate <= :followUpBefore");
            params.put("followUpBefore", query.followUpBefore());
        }

        if (query.expiresAtOrBefore() != null) {
            if (jpql.length() > 0) {
                jpql.append(" AND ");
            }
            jpql.append("expiresAt <= :expiresAtOrBefore");
            params.put("expiresAtOrBefore", query.expiresAtOrBefore());
        }

        if (query.claimDeadlineOrBefore() != null) {
            if (jpql.length() > 0) {
                jpql.append(" AND ");
            }
            jpql.append("claimDeadline <= :claimDeadlineOrBefore");
            params.put("claimDeadlineOrBefore", query.claimDeadlineOrBefore());
        }

        // ── Label pattern — requires JOIN ────────────────────────────────────
        if (query.labelPattern() != null) {
            return scanByLabelPattern(query.labelPattern());
        }

        // ── No constraints — return all ──────────────────────────────────────
        if (jpql.length() == 0) {
            return WorkItem.listAll();
        }

        return WorkItem.find(jpql.toString(), params).list();
    }

    /**
     * Label pattern scan using the existing JPQL JOIN approach from {@code JpaWorkItemRepository}.
     *
     * @param pattern the label pattern; must not be null
     * @return matching work items
     */
    private List<WorkItem> scanByLabelPattern(final String pattern) {
        if (pattern.endsWith("/**")) {
            final String prefix = pattern.substring(0, pattern.length() - 3) + "/";
            return WorkItem.<WorkItem> find(
                    "SELECT DISTINCT wi FROM WorkItem wi JOIN wi.labels l WHERE l.path LIKE ?1",
                    prefix + "%").list();
        }
        if (pattern.endsWith("/*")) {
            final String prefix = pattern.substring(0, pattern.length() - 2) + "/";
            return WorkItem.<WorkItem> find(
                    "SELECT DISTINCT wi FROM WorkItem wi JOIN wi.labels l " +
                            "WHERE l.path LIKE ?1 AND l.path NOT LIKE ?2",
                    prefix + "%", prefix + "%/%").list();
        }
        return WorkItem.<WorkItem> find(
                "SELECT DISTINCT wi FROM WorkItem wi JOIN wi.labels l WHERE l.path = ?1",
                pattern).list();
    }
}
