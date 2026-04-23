package io.quarkiverse.work.runtime.model;

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
 * A structured reference from a {@link WorkItem} to an external resource.
 *
 * <h2>What this is for</h2>
 * <p>
 * A WorkItem doing a loan approval might reference the loan application document
 * in S3. A security triage WorkItem might link to the CVE description and the
 * internal design spec. A compliance review needs the policy document being reviewed.
 * These references are operational context — they belong to the work unit.
 *
 * <h2>What this is NOT</h2>
 * <ul>
 * <li><strong>Not a file store</strong> — WorkItems stores the URL reference only.
 * Store file content in S3, GCS, MinIO, or SharePoint; link to it here with
 * {@link WorkItemLinkType#ATTACHMENT}.</li>
 * <li><strong>Not a WorkItemIssueLink</strong> — {@code WorkItemIssueLink} links to
 * tracked tickets in GitHub/Jira with sync and auto-close semantics. This
 * links to <em>any</em> external resource by URL.</li>
 * </ul>
 *
 * <h2>Pluggable relation types</h2>
 * <p>
 * {@link #relationType} is a plain string. Use {@link WorkItemLinkType} constants
 * for well-known types, or any non-blank string for domain-specific types
 * ({@code "runbook"}, {@code "internal-wiki"}, {@code "customer-ticket"}).
 */
@Entity
@Table(name = "work_item_link")
public class WorkItemLink extends PanacheEntityBase {

    @Id
    public UUID id;

    /** The WorkItem this link is attached to. Cascade-deleted with the WorkItem. */
    @Column(name = "work_item_id", nullable = false)
    public UUID workItemId;

    /** The URL of the external resource. */
    @Column(nullable = false, length = 2000)
    public String url;

    /**
     * Optional human-readable title for display.
     * If null, callers should fall back to the URL hostname.
     */
    @Column(length = 500)
    public String title;

    /**
     * The relationship type — a plain string.
     * Use {@link WorkItemLinkType} constants for well-known types;
     * any non-blank string is accepted for custom types.
     */
    @Column(name = "relation_type", nullable = false, length = 100)
    public String relationType;

    /** The actor who added this link. */
    @Column(name = "linked_by", nullable = false, length = 255)
    public String linkedBy;

    /** When this link was created. */
    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @PrePersist
    void prePersist() {
        if (id == null)
            id = UUID.randomUUID();
        if (createdAt == null)
            createdAt = Instant.now();
    }

    /** All links for a WorkItem, ordered chronologically. */
    public static List<WorkItemLink> findByWorkItemId(final UUID workItemId) {
        return list("workItemId = ?1 ORDER BY createdAt ASC", workItemId);
    }

    /** Links for a WorkItem filtered to a specific relation type. */
    public static List<WorkItemLink> findByWorkItemIdAndType(
            final UUID workItemId, final String relationType) {
        return list("workItemId = ?1 AND relationType = ?2 ORDER BY createdAt ASC",
                workItemId, relationType);
    }
}
