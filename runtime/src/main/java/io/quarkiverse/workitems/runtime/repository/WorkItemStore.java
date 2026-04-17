package io.quarkiverse.workitems.runtime.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.quarkiverse.workitems.runtime.model.WorkItem;

/**
 * KV-native store SPI for {@link WorkItem} persistence.
 *
 * <p>
 * Replaces the SQL-shaped {@code WorkItemRepository} with a store interface
 * that separates primary-key operations ({@link #put}, {@link #get}) from
 * query operations ({@link #scan}) using the {@link WorkItemQuery} value object.
 * Backends translate {@code WorkItemQuery} to their native query language.
 *
 * <p>
 * The default implementation uses Hibernate ORM with Panache. Alternatives
 * (MongoDB, Redis, in-memory) substitute via CDI {@code @Alternative @Priority(1)}.
 *
 * @see WorkItemQuery
 */
public interface WorkItemStore {

    /**
     * Persist or update a WorkItem and return the saved instance.
     * Replaces the former {@code save()} method — aligned with KV store terminology.
     *
     * @param workItem the work item to persist; must not be {@code null}
     * @return the persisted work item
     */
    WorkItem put(WorkItem workItem);

    /**
     * Retrieve a WorkItem by its primary key.
     *
     * @param id the UUID primary key
     * @return an {@link Optional} containing the work item, or empty if not found
     */
    Optional<WorkItem> get(UUID id);

    /**
     * Scan WorkItems matching the given query criteria.
     *
     * <p>
     * Assignment fields in the query are combined with OR logic; all other fields
     * are combined with AND logic. A {@code null} field imposes no constraint.
     *
     * <p>
     * Use {@link WorkItemQuery} static factories for common patterns:
     * {@link WorkItemQuery#inbox inbox}, {@link WorkItemQuery#expired expired},
     * {@link WorkItemQuery#claimExpired claimExpired},
     * {@link WorkItemQuery#byLabelPattern byLabelPattern}.
     *
     * @param query the query criteria; must not be {@code null}
     * @return list of matching work items; may be empty, never null
     */
    List<WorkItem> scan(WorkItemQuery query);

    /**
     * Return all WorkItems — for admin and monitoring use only.
     * Equivalent to {@code scan(WorkItemQuery.all())}.
     *
     * @return unordered list of all persisted work items
     */
    default List<WorkItem> scanAll() {
        return scan(WorkItemQuery.all());
    }
}
