package io.quarkiverse.workitems.ai.escalation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

/**
 * LLM-generated summary of a WorkItem's history at the moment it escalates.
 *
 * <p>
 * Created by {@link EscalationSummaryObserver} when a {@code EXPIRED} or
 * {@code CLAIM_EXPIRED} lifecycle event fires. Provides the escalation target
 * with immediate context without requiring them to read the full audit trail.
 */
@Entity
@Table(name = "escalation_summary")
public class EscalationSummary extends PanacheEntityBase {

    @Id
    @Column(nullable = false)
    public UUID id;

    @Column(name = "work_item_id", nullable = false)
    public UUID workItemId;

    /** {@code EXPIRED} or {@code CLAIM_EXPIRED}. */
    @Column(name = "event_type", nullable = false, length = 50)
    public String eventType;

    /** LLM-generated plain-text briefing; {@code null} when no model was available. */
    @Column(columnDefinition = "TEXT")
    public String summary;

    @Column(name = "generated_at", nullable = false)
    public Instant generatedAt;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (generatedAt == null) {
            generatedAt = Instant.now();
        }
    }

    /** Return all summaries for a WorkItem, most recent first. */
    public static List<EscalationSummary> findByWorkItemId(final UUID workItemId) {
        return list("workItemId = ?1 ORDER BY generatedAt DESC", workItemId);
    }
}
