package io.casehub.work.runtime.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

/**
 * An internal operational note attached to a {@link WorkItem}.
 *
 * <h2>What a note is for</h2>
 * <p>
 * Notes capture the operational <em>why</em> — context that belongs to the work unit
 * itself and is visible to the assignee chain but should not leave your system:
 * <ul>
 * <li>"Delegated to Carol because Alice is on leave until Friday."</li>
 * <li>"Found a related compliance gap during initial review — flagged separately."</li>
 * <li>"Model confidence was 0.71; escalated because threshold is 0.8."</li>
 * </ul>
 *
 * <h2>What a note is NOT</h2>
 * <ul>
 * <li><strong>Not an audit entry</strong> — {@link AuditEntry} records are automatic,
 * structured, and immutable. Notes are human-authored, free-text, and editable.</li>
 * <li><strong>Not an issue tracker comment</strong> — GitHub Issues, Jira, Linear
 * comments are for external stakeholders and community discussion. Notes are
 * internal to your operational workflow and may contain sensitive data.</li>
 * </ul>
 *
 * <h2>Decision rule</h2>
 * <p>
 * "Why did this WorkItem get delegated?" → {@code WorkItemNote}<br>
 * "Why was this bug filed?" → GitHub Issue comment
 *
 * <h2>Editability</h2>
 * <p>
 * Notes are editable ({@link #editedAt} records the last modification) and deletable.
 * They are NOT part of the cryptographic audit trail — use {@link AuditEntry} (or the
 * ledger module) for tamper-evident records.
 */
@Entity
@Table(name = "work_item_note")
public class WorkItemNote extends PanacheEntityBase {

    /** Primary key — UUID assigned on first persist. */
    @Id
    public UUID id;

    /** The WorkItem this note is attached to. Cascade-deleted with the WorkItem. */
    @Column(name = "work_item_id", nullable = false)
    public UUID workItemId;

    /** The note's free-text content. May contain markdown. */
    @Column(nullable = false, columnDefinition = "TEXT")
    public String content;

    /** Identity of the actor who wrote this note (user or system). */
    @Column(nullable = false, length = 255)
    public String author;

    /** When this note was first created. */
    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    /**
     * When this note was last edited, or {@code null} if never edited.
     * Set by the application whenever {@link #content} is updated.
     */
    @Column(name = "edited_at")
    public Instant editedAt;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
