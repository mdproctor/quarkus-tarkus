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
 * JSON Schema definition for the payload and/or resolution of WorkItems in a category.
 *
 * <h2>What this is for</h2>
 * <p>
 * A {@code WorkItemFormSchema} tells UI developers what shape the {@code payload} and
 * {@code resolution} fields of a WorkItem are expected to have. Without this, every UI
 * developer must inspect application source code or guess. With it, a UI can
 * {@code GET /workitem-form-schemas?category=loan-approval} and auto-generate a
 * validated form — no source code access needed.
 *
 * <h2>Schema storage</h2>
 * <p>
 * Both {@link #payloadSchema} and {@link #resolutionSchema} are stored as TEXT — valid
 * JSON but not further parsed or validated by WorkItems. Validation against the schema
 * happens on the client or in a later phase (#108).
 *
 * <h2>Category is optional</h2>
 * <p>
 * A schema with a null {@link #category} is a global/catch-all definition not tied to
 * a specific work type. Schemas with a category apply to WorkItems in that category.
 *
 * @see <a href="https://github.com/mdproctor/quarkus-work/issues/107">Issue #107</a>
 * @see <a href="https://github.com/mdproctor/quarkus-work/issues/98">Epic #98</a>
 */
@Entity
@Table(name = "work_item_form_schema")
public class WorkItemFormSchema extends PanacheEntityBase {

    @Id
    public UUID id;

    /** Display name for this schema definition. Required. */
    @Column(nullable = false, length = 255)
    public String name;

    /**
     * The WorkItem category this schema applies to.
     * Null means the schema is not category-scoped (global/catch-all).
     */
    @Column(length = 255)
    public String category;

    /**
     * JSON Schema (draft-07) defining the expected shape of {@code WorkItem.payload}.
     * Stored as TEXT; not validated by WorkItems on write.
     */
    @Column(name = "payload_schema", columnDefinition = "TEXT")
    public String payloadSchema;

    /**
     * JSON Schema (draft-07) defining the expected shape of the resolution object
     * submitted when completing a WorkItem.
     * Stored as TEXT; not validated by WorkItems on write.
     */
    @Column(name = "resolution_schema", columnDefinition = "TEXT")
    public String resolutionSchema;

    /**
     * Free-form version identifier for this schema (e.g. "1.0", "2024-Q1").
     * Useful when the same category has schema evolution over time.
     */
    @Column(name = "schema_version", length = 50)
    public String schemaVersion;

    /** The actor who created this schema definition. Required. */
    @Column(name = "created_by", nullable = false, length = 255)
    public String createdBy;

    /** When this schema was created. */
    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @PrePersist
    void prePersist() {
        if (id == null)
            id = UUID.randomUUID();
        if (createdAt == null)
            createdAt = Instant.now();
    }

    /** All schemas, ordered by name. */
    public static List<WorkItemFormSchema> listAllByName() {
        return list("ORDER BY name ASC");
    }

    /** All schemas for a specific category, ordered by name. */
    public static List<WorkItemFormSchema> findByCategory(final String category) {
        return list("category = ?1 ORDER BY name ASC", category);
    }

    /**
     * The most recently created schema for a category, for use during validation.
     *
     * <p>
     * When multiple schemas exist for the same category (e.g. after a schema evolution),
     * the one with the latest {@link #createdAt} is used as the authoritative definition.
     * Returns {@code null} if no schema exists for the category.
     */
    public static WorkItemFormSchema findLatestByCategory(final String category) {
        return find("category = ?1 ORDER BY createdAt DESC", category)
                .firstResult();
    }
}
