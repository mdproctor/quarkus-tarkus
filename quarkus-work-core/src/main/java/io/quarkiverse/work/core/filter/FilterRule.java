package io.quarkiverse.work.core.filter;

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
 * DB-persisted filter rule — the dynamic counterpart to CDI-produced permanent
 * {@link FilterDefinition} beans.
 */
@Entity
@Table(name = "filter_rule")
public class FilterRule extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(nullable = false, length = 255)
    public String name;

    @Column(length = 500)
    public String description;

    @Column(nullable = false)
    public boolean enabled = true;

    @Column(nullable = false, columnDefinition = "TEXT")
    public String condition;

    @Column(nullable = false, length = 50)
    public String events = "ADD,UPDATE,REMOVE";

    @Column(name = "actions_json", nullable = false, columnDefinition = "TEXT")
    public String actionsJson = "[]";

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    /** Sets defaults before first persist. */
    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    /**
     * Returns all enabled filter rules, ordered by creation time ascending.
     *
     * @return list of enabled FilterRule entities
     */
    public static List<FilterRule> allEnabled() {
        return list("enabled = true ORDER BY createdAt ASC");
    }
}
