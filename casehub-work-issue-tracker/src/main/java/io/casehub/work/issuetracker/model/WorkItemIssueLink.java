package io.casehub.work.issuetracker.model;

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
 * Persistent link between a {@link io.casehub.work.runtime.model.WorkItem}
 * and an issue in an external issue tracker.
 *
 * <h2>externalRef format</h2>
 * <p>
 * Each tracker provider defines its own format. The GitHub provider uses
 * {@code "owner/repo#42"} (self-contained — no extra config needed per link).
 * Jira might use {@code "PROJ-1234"}. The format is opaque to the rest of the
 * system; only the provider that created the link can interpret it.
 *
 * <h2>Status caching</h2>
 * <p>
 * {@link #status} is a cached hint — it reflects the remote issue state at the
 * time of linking or the last sync ({@code PUT /workitems/{id}/issues/sync}).
 * The issue tracker is the source of truth for its own state. Stale values are
 * expected and benign.
 *
 * <h2>Lifecycle</h2>
 * <p>
 * There is no DB-level FK from {@code work_item_id} to {@code work_item} — WorkItems may
 * live in any backend (JPA, MongoDB, Redis). Application-level cleanup is the caller's
 * responsibility. A link can be removed explicitly via {@code DELETE /workitems/{id}/issues/{linkId}}.
 */
@Entity
@Table(name = "work_item_issue_link", uniqueConstraints = @UniqueConstraint(name = "uq_work_item_issue_link", columnNames = {
        "work_item_id", "tracker_type", "external_ref" }))
public class WorkItemIssueLink extends PanacheEntityBase {

    /** Surrogate primary key. */
    @Id
    public UUID id;

    /** The WorkItem this link belongs to. */
    @Column(name = "work_item_id", nullable = false)
    public UUID workItemId;

    /**
     * The type of issue tracker: {@code "github"}, {@code "jira"}, {@code "linear"}, etc.
     * Must match the {@code trackerType()} of the provider that created this link.
     */
    @Column(name = "tracker_type", nullable = false, length = 50)
    public String trackerType;

    /**
     * Tracker-specific issue reference. Format is defined by the provider.
     * GitHub: {@code "owner/repo#42"}. Jira: {@code "PROJ-1234"}.
     */
    @Column(name = "external_ref", nullable = false, length = 500)
    public String externalRef;

    /** Cached title of the issue at link time (or last sync). May be null if fetch failed. */
    @Column(length = 500)
    public String title;

    /** Direct URL to the issue in its tracker. May be null if not provided by the tracker. */
    @Column(length = 2000)
    public String url;

    /**
     * Last-known status of the issue: {@code "open"}, {@code "closed"}, or {@code "unknown"}.
     * Updated on link creation and on explicit sync. Not automatically kept in sync.
     */
    @Column(nullable = false, length = 50)
    public String status = "unknown";

    /** When this link was created. */
    @Column(name = "linked_at", nullable = false)
    public Instant linkedAt;

    /** The actor (user or system) that created this link. */
    @Column(name = "linked_by", nullable = false, length = 255)
    public String linkedBy;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (linkedAt == null) {
            linkedAt = Instant.now();
        }
    }

    /**
     * Return all links for the given WorkItem, ordered by creation time ascending.
     *
     * @param workItemId the WorkItem UUID
     * @return list of links; may be empty
     */
    public static List<WorkItemIssueLink> findByWorkItemId(final UUID workItemId) {
        return list("workItemId = ?1 ORDER BY linkedAt ASC", workItemId);
    }

    /**
     * Return a specific link by WorkItem and tracker reference.
     *
     * @param workItemId the WorkItem UUID
     * @param trackerType the tracker type string
     * @param externalRef the tracker-specific reference
     * @return the link, or null if not found
     */
    public static WorkItemIssueLink findByRef(
            final UUID workItemId, final String trackerType, final String externalRef) {
        return find("workItemId = ?1 AND trackerType = ?2 AND externalRef = ?3",
                workItemId, trackerType, externalRef).firstResult();
    }
}
