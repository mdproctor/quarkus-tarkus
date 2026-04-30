package io.casehub.work.runtime.model;

import java.time.Instant;
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
 * A directed relation between two {@link WorkItem} instances.
 *
 * <h2>Directionality</h2>
 * <p>
 * Every relation is directed: {@code source → target}.
 * Reading as a sentence: "{@code source} [relationType] {@code target}":
 * <ul>
 * <li>{@code child} <strong>PART_OF</strong> {@code parent}</li>
 * <li>{@code taskA} <strong>BLOCKS</strong> {@code taskB}</li>
 * <li>{@code issue} <strong>DUPLICATES</strong> {@code canonicalIssue}</li>
 * </ul>
 *
 * <h2>Pluggable relation types</h2>
 * <p>
 * {@link #relationType} is a plain string — not an enum. Well-known constants
 * are in {@link WorkItemRelationType}; custom types ({@code "TRIGGERED_BY"},
 * {@code "APPROVED_BY"}) are accepted without any schema change.
 *
 * <h2>Cycle prevention</h2>
 * <p>
 * For {@link WorkItemRelationType#PART_OF} relations, the application layer
 * enforces that no WorkItem can be its own ancestor. This check requires graph
 * traversal and is performed in {@link io.casehub.work.runtime.api.WorkItemRelationResource}
 * before the relation is persisted.
 */
@Entity
@Table(name = "work_item_relation", uniqueConstraints = @UniqueConstraint(name = "uq_work_item_relation", columnNames = {
        "source_id", "target_id", "relation_type" }))
public class WorkItemRelation extends PanacheEntityBase {

    /** Surrogate primary key. */
    @Id
    public UUID id;

    /** The WorkItem where the relation originates. */
    @Column(name = "source_id", nullable = false)
    public UUID sourceId;

    /** The WorkItem the relation points to. */
    @Column(name = "target_id", nullable = false)
    public UUID targetId;

    /**
     * The type of relation — a plain string, not an enum.
     * Use constants from {@link WorkItemRelationType} for well-known types,
     * or any non-blank string for custom types.
     */
    @Column(name = "relation_type", nullable = false, length = 100)
    public String relationType;

    /** The actor who created this relation. */
    @Column(name = "created_by", nullable = false, length = 255)
    public String createdBy;

    /** When this relation was created. */
    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    /** All outgoing relations from a given WorkItem, ordered by creation time. */
    public static List<WorkItemRelation> findBySourceId(final UUID sourceId) {
        return list("sourceId = ?1 ORDER BY createdAt ASC", sourceId);
    }

    /** All incoming relations pointing to a given WorkItem, ordered by creation time. */
    public static List<WorkItemRelation> findByTargetId(final UUID targetId) {
        return list("targetId = ?1 ORDER BY createdAt ASC", targetId);
    }

    /** Outgoing relations of a specific type from a given WorkItem. */
    public static List<WorkItemRelation> findBySourceAndType(
            final UUID sourceId, final String relationType) {
        return list("sourceId = ?1 AND relationType = ?2 ORDER BY createdAt ASC",
                sourceId, relationType);
    }

    /** Incoming relations of a specific type pointing to a given WorkItem. */
    public static List<WorkItemRelation> findByTargetAndType(
            final UUID targetId, final String relationType) {
        return list("targetId = ?1 AND relationType = ?2 ORDER BY createdAt ASC",
                targetId, relationType);
    }

    /** Find an existing relation by all three keys — used for duplicate detection. */
    public static WorkItemRelation findExisting(
            final UUID sourceId, final UUID targetId, final String relationType) {
        return find("sourceId = ?1 AND targetId = ?2 AND relationType = ?3",
                sourceId, targetId, relationType).firstResult();
    }
}
