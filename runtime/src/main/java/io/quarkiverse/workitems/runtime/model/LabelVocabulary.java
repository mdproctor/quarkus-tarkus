package io.quarkiverse.workitems.runtime.model;

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
 * A named, scoped container of {@link LabelDefinition} entries.
 *
 * <p>
 * Vocabularies form a visibility hierarchy: GLOBAL → ORG → TEAM → PERSONAL.
 * A user can apply any label declared in a vocabulary at or above their scope.
 */
@Entity
@Table(name = "label_vocabulary")
public class LabelVocabulary extends PanacheEntityBase {

    @Id
    public UUID id;

    /** Scope of this vocabulary. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public VocabularyScope scope;

    /**
     * Owner: null for GLOBAL, orgId for ORG, groupId for TEAM, userId for PERSONAL.
     */
    @Column(name = "owner_id", length = 255)
    public String ownerId;

    /** Human-readable name for this vocabulary. */
    @Column(nullable = false, length = 255)
    public String name;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }
}
