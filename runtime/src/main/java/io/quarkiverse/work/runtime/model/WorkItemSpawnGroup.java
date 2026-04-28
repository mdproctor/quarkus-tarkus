package io.quarkiverse.work.runtime.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

/**
 * Tracks a batch of child WorkItems spawned together from a common parent.
 *
 * <h2>Purpose</h2>
 * <p>
 * Used for two things only:
 * <ol>
 * <li>Idempotency — the unique constraint on {@code (parent_id, idempotency_key)}
 * ensures a retried spawn call returns the existing group without creating
 * duplicate children.</li>
 * <li>Group membership query — callers can retrieve all children by querying
 * PART_OF relations with {@code targetId = parentId} that were created after
 * {@link #createdAt}; or via {@code GET /workitems/{parentId}/children}.</li>
 * </ol>
 *
 * <h2>No completion state</h2>
 * <p>
 * This entity does NOT track completion state, completion policy, or group status.
 * That is the caller's concern (CaseHub's {@code Stage.requiredItemIds};
 * or the application's own CDI observer). quarkus-work fires per-child
 * {@link io.quarkiverse.work.runtime.event.WorkItemLifecycleEvent}s — the caller
 * decides what they mean.
 */
@Entity
@Table(name = "work_item_spawn_group", uniqueConstraints = {
        @UniqueConstraint(name = "uq_spawn_group_idempotency", columnNames = { "parent_id", "idempotency_key" })
})
public class WorkItemSpawnGroup extends PanacheEntityBase {

    @Id
    public UUID id;

    /** The parent WorkItem that owns this spawn group. */
    @Column(name = "parent_id", nullable = false)
    public UUID parentId;

    /**
     * Caller-supplied deduplication key. A second spawn call with the same
     * {@code (parentId, idempotencyKey)} returns this group instead of creating a new one.
     */
    @Column(name = "idempotency_key", nullable = false, length = 255)
    public String idempotencyKey;

    /** When this group was created. */
    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    /** OCC version — incremented on every successful counter update. */
    @Version
    @Column(nullable = false)
    public Long version = 0L;

    /** instanceCount from MultiInstanceConfig; null for non-multi-instance groups. */
    @Column(name = "instance_count")
    public Integer instanceCount;

    /** requiredCount from MultiInstanceConfig; null for non-multi-instance groups. */
    @Column(name = "required_count")
    public Integer requiredCount;

    /** What to do with remaining non-terminal children when threshold is met. */
    @Column(name = "on_threshold_reached", length = 10)
    public String onThresholdReached;

    /** When false, a claimant already holding another instance in this group is rejected. */
    @Column(name = "allow_same_assignee", nullable = false)
    public boolean allowSameAssignee = false;

    /** COORDINATOR or PARTICIPANT. */
    @Column(name = "parent_role", length = 15)
    public String parentRole;

    /** Number of child instances that have reached COMPLETED status. */
    @Column(name = "completed_count", nullable = false)
    public int completedCount = 0;

    /** Number of child instances that have reached a non-COMPLETED terminal status. */
    @Column(name = "rejected_count", nullable = false)
    public int rejectedCount = 0;

    /** True once the group outcome has been determined and the parent transitioned. */
    @Column(name = "policy_triggered", nullable = false)
    public boolean policyTriggered = false;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    /** Find an existing group by parent + idempotency key. */
    public static WorkItemSpawnGroup findByParentAndKey(
            final UUID parentId, final String idempotencyKey) {
        return find("parentId = ?1 AND idempotencyKey = ?2", parentId, idempotencyKey)
                .firstResult();
    }

    /** All groups spawned from a parent, newest first. */
    public static List<WorkItemSpawnGroup> findByParentId(final UUID parentId) {
        return list("parentId = ?1 ORDER BY createdAt DESC", parentId);
    }

    /**
     * Find the multi-instance spawn group for a parent — the group where
     * {@code requiredCount} is set. Returns null if no multi-instance group exists.
     */
    public static WorkItemSpawnGroup findMultiInstanceByParentId(final UUID parentId) {
        return find("parentId = ?1 AND requiredCount IS NOT NULL", parentId).firstResult();
    }
}
