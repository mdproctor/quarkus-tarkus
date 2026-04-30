package io.casehub.work.runtime.repository.jpa;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;

import io.casehub.work.api.GroupStatus;
import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.runtime.model.WorkItemRootView;
import io.casehub.work.runtime.model.WorkItemSpawnGroup;
import io.casehub.work.runtime.repository.WorkItemQuery;
import io.casehub.work.runtime.repository.WorkItemStore;

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

    @Override
    public long countByParentAndAssignee(final UUID parentId, final String assigneeId, final UUID excludeId) {
        // Only count non-terminal instances — terminal children no longer block new claims
        return WorkItem.count(
                "parentId = ?1 AND assigneeId = ?2 AND id != ?3 AND status NOT IN (?4)",
                parentId, assigneeId, excludeId,
                List.of(io.casehub.work.runtime.model.WorkItemStatus.COMPLETED,
                        io.casehub.work.runtime.model.WorkItemStatus.REJECTED,
                        io.casehub.work.runtime.model.WorkItemStatus.CANCELLED,
                        io.casehub.work.runtime.model.WorkItemStatus.ESCALATED));
    }

    @Override
    public List<WorkItemRootView> scanRoots(final String userId, final List<String> userGroups) {
        // Build visibility predicate using named params (same pattern as scan())
        final StringBuilder pred = new StringBuilder();
        final Map<String, Object> params = new HashMap<>();

        if (userId != null && !userId.isBlank()) {
            if (!pred.isEmpty()) {
                pred.append(" OR ");
            }
            pred.append("assigneeId = :assigneeId OR candidateUsers LIKE :userIdLike");
            params.put("assigneeId", userId);
            params.put("userIdLike", "%" + userId + "%");
        }
        if (userGroups != null) {
            int gi = 0;
            for (final String group : userGroups) {
                final String key = "grp" + gi++;
                if (!pred.isEmpty()) {
                    pred.append(" OR ");
                }
                pred.append("candidateGroups LIKE :").append(key);
                params.put(key, "%" + group + "%");
            }
        }
        if (pred.isEmpty()) {
            return java.util.Collections.emptyList();
        }

        // Find directly visible items
        final List<WorkItem> directlyVisible = WorkItem.find(pred.toString(), params).list();

        // Collect roots (items with parentId IS NULL) including ancestors of visible children
        final LinkedHashSet<UUID> rootIds = new LinkedHashSet<>();
        final LinkedHashMap<UUID, WorkItem> rootItems = new LinkedHashMap<>();

        for (final WorkItem item : directlyVisible) {
            if (item.parentId == null) {
                rootIds.add(item.id);
                rootItems.put(item.id, item);
            } else {
                final WorkItem parent = WorkItem.findById(item.parentId);
                if (parent != null && parent.parentId == null) {
                    rootIds.add(parent.id);
                    rootItems.put(parent.id, parent);
                }
            }
        }

        return rootIds.stream().map(id -> {
            final WorkItem root = rootItems.get(id);
            final WorkItemSpawnGroup group = WorkItemSpawnGroup.findMultiInstanceByParentId(id);
            final int childCount = (int) WorkItem.count("parentId", id);
            if (group != null) {
                final GroupStatus status = group.policyTriggered
                        ? (group.completedCount >= group.requiredCount
                                ? GroupStatus.COMPLETED
                                : GroupStatus.REJECTED)
                        : GroupStatus.IN_PROGRESS;
                return new WorkItemRootView(root, childCount, group.completedCount, group.requiredCount, status);
            }
            return new WorkItemRootView(root, childCount, null, null, null);
        }).toList();
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
