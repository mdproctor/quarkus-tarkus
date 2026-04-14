package io.quarkiverse.tarkus.ledger.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

/**
 * An immutable record in the WorkItem ledger.
 *
 * <p>
 * Each {@code LedgerEntry} records either a command (intent) or an event (fact) for a
 * single WorkItem lifecycle transition. Entries are sequenced per WorkItem and optionally
 * chained by SHA-256 digest to provide tamper evidence (Certificate Transparency pattern).
 *
 * <p>
 * The {@code decisionContext} field carries a JSON snapshot of the WorkItem state at the
 * moment of the transition — required by GDPR Article 22 and EU AI Act Article 12 for
 * point-in-time auditability.
 */
@Entity
@Table(name = "ledger_entry")
public class LedgerEntry extends PanacheEntityBase {

    /** Primary key — UUID assigned on first persist. */
    @Id
    public UUID id;

    /** The WorkItem this entry belongs to. */
    @Column(name = "work_item_id", nullable = false)
    public UUID workItemId;

    /** Position of this entry in the per-WorkItem ledger sequence (1-based). */
    @Column(name = "sequence_number", nullable = false)
    public int sequenceNumber;

    /** Whether this entry is a command, an event, or an attestation record. */
    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false)
    public LedgerEntryType entryType;

    /** The intent expressed by the actor — e.g. {@code "CompleteWorkItem"}. Nullable. */
    @Column(name = "command_type")
    public String commandType;

    /** The observable fact after execution — e.g. {@code "WorkItemCompleted"}. Nullable. */
    @Column(name = "event_type")
    public String eventType;

    /** Identity of the actor who triggered the transition. */
    @Column(name = "actor_id")
    public String actorId;

    /** Whether the actor is a human, autonomous agent, or the system itself. */
    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type")
    public ActorType actorType;

    /** The functional role of the actor in this transition — e.g. {@code "Resolver"}. */
    @Column(name = "actor_role")
    public String actorRole;

    /**
     * Reference to the policy or procedure version that governed this action.
     * Null when the actor did not supply a plan reference.
     */
    @Column(name = "plan_ref")
    public String planRef;

    /**
     * The actor's stated basis for the decision. Null when not provided.
     * Taken directly from {@code WorkItemLifecycleEvent.rationale()}.
     */
    @Column(columnDefinition = "TEXT")
    public String rationale;

    /**
     * JSON snapshot of the WorkItem state at the moment of this transition.
     * Populated when {@code quarkus.tarkus.ledger.decision-context.enabled=true}.
     */
    @Column(name = "decision_context", columnDefinition = "TEXT")
    public String decisionContext;

    /**
     * Structured evidence supplied by the actor. Null unless
     * {@code quarkus.tarkus.ledger.evidence.enabled=true} and the caller supplies evidence.
     */
    @Column(columnDefinition = "TEXT")
    public String evidence;

    /**
     * Optional free-text or JSON detail forwarded from the lifecycle event.
     * Carries delegation targets, rejection reasons, and similar transition-specific data.
     */
    @Column(columnDefinition = "TEXT")
    public String detail;

    /** FK to the ledger entry that causally produced this entry. Null for direct transitions. */
    @Column(name = "caused_by_entry_id")
    public UUID causedByEntryId;

    /** OpenTelemetry trace ID linking this entry to a distributed trace. Null until OTel integration ships. */
    @Column(name = "correlation_id")
    public String correlationId;

    /** Identifier of the external entity (e.g. workflow instance) that created the WorkItem. */
    @Column(name = "source_entity_id")
    public String sourceEntityId;

    /** Type of the external entity — e.g. {@code "Flow:WorkflowInstance"}. */
    @Column(name = "source_entity_type")
    public String sourceEntityType;

    /** The system that owns the external entity — e.g. {@code "quarkus-flow"}. */
    @Column(name = "source_entity_system")
    public String sourceEntitySystem;

    /**
     * SHA-256 digest of the previous entry for this WorkItem.
     * {@code "GENESIS"} for the first entry. Null when hash chain is disabled.
     */
    @Column(name = "previous_hash")
    public String previousHash;

    /**
     * SHA-256 digest of this entry's canonical content chained from {@code previousHash}.
     * Null when hash chain is disabled ({@code quarkus.tarkus.ledger.hash-chain.enabled=false}).
     */
    public String digest;

    /** When this entry was recorded — set automatically on first persist. */
    @Column(name = "occurred_at", nullable = false)
    public Instant occurredAt;

    /**
     * Assigns a UUID primary key and sets {@code occurredAt} before the entity is inserted.
     */
    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (occurredAt == null) {
            occurredAt = Instant.now();
        }
    }
}
