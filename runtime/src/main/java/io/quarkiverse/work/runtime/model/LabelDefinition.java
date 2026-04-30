package io.casehub.work.runtime.model;

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
 * A declared label path within a {@link LabelVocabulary}.
 *
 * <p>
 * Before a label path can be applied to a WorkItem, it must be declared as a
 * {@code LabelDefinition} in a vocabulary accessible to the actor.
 */
@Entity
@Table(name = "label_definition")
public class LabelDefinition extends PanacheEntityBase {

    @Id
    public UUID id;

    /** The full label path, e.g. {@code legal/contracts/nda}. */
    @Column(nullable = false, length = 500)
    public String path;

    /** The vocabulary this definition belongs to. */
    @Column(name = "vocabulary_id", nullable = false)
    public UUID vocabularyId;

    /** Optional human-readable description of what this label means. */
    @Column(length = 1000)
    public String description;

    /** The user who declared this label. */
    @Column(name = "created_by", nullable = false, length = 255)
    public String createdBy;

    /** When this definition was created. */
    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = Instant.now();
    }

    /** Find all definitions for a given vocabulary. */
    public static List<LabelDefinition> findByVocabularyId(final UUID vocabularyId) {
        return find("vocabularyId", vocabularyId).list();
    }

    /** Find by exact path across all vocabularies. */
    public static List<LabelDefinition> findByPath(final String path) {
        return find("path", path).list();
    }
}
