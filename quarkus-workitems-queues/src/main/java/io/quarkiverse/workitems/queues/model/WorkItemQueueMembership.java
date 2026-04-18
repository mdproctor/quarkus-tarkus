package io.quarkiverse.workitems.queues.model;

import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

/**
 * Persistent record of a WorkItem's membership in a single {@link QueueView}.
 *
 * <p>
 * The {@code work_item_queue_membership} table is the authoritative "before-state"
 * used by {@link io.quarkiverse.workitems.queues.service.QueueMembershipContext} to
 * diff queue membership changes across JVM restarts. Without this table the tracker
 * would be in-memory only, and every restart would re-fire {@code ADDED} events for
 * all items currently in any queue.
 *
 * <p>
 * Managed exclusively by {@link io.quarkiverse.workitems.queues.service.QueueMembershipTracker}.
 * Never written or read by any other component.
 *
 * <h2>Lifecycle</h2>
 * <ul>
 * <li>Rows are inserted (or replaced) each time {@code QueueMembershipTracker.update()} runs.</li>
 * <li>All rows for a given {@code workItemId} are deleted and re-inserted atomically
 * within the same transaction as the {@code WorkItemLifecycleEvent} that triggered them.</li>
 * <li>When a WorkItem leaves all queues, all its rows are deleted — no orphan rows.</li>
 * </ul>
 */
@Entity
@Table(name = "work_item_queue_membership", uniqueConstraints = @UniqueConstraint(name = "uq_work_item_queue_membership", columnNames = {
        "work_item_id", "queue_view_id" }))
public class WorkItemQueueMembership extends PanacheEntityBase {

    /** Surrogate primary key — UUID assigned on first persist. */
    @Id
    public UUID id;

    /** The WorkItem that is a member of the queue. */
    @Column(name = "work_item_id", nullable = false)
    public UUID workItemId;

    /** The {@link QueueView} the WorkItem belongs to. */
    @Column(name = "queue_view_id", nullable = false)
    public UUID queueViewId;

    /**
     * The queue's display name at the time of recording.
     * Stored to avoid a join when constructing {@link io.quarkiverse.workitems.queues.event.WorkItemQueueEvent}
     * instances for REMOVED events (the QueueView may no longer exist at that point).
     */
    @Column(name = "queue_name", nullable = false, length = 255)
    public String queueName;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }

    /**
     * Return all membership rows for the given WorkItem, ordered by queue name.
     *
     * @param workItemId the WorkItem UUID
     * @return list of membership rows; may be empty
     */
    public static List<WorkItemQueueMembership> findByWorkItemId(final UUID workItemId) {
        return list("workItemId = ?1 ORDER BY queueName ASC", workItemId);
    }

    /**
     * Delete all membership rows for the given WorkItem.
     * Called by the tracker before inserting the updated membership set.
     *
     * @param workItemId the WorkItem UUID
     */
    public static void deleteByWorkItemId(final UUID workItemId) {
        delete("workItemId", workItemId);
    }
}
