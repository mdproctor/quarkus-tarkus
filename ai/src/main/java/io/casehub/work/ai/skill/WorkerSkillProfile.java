package io.casehub.work.ai.skill;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

/**
 * Stores a free-text skill narrative for a worker.
 * Used by {@link WorkerProfileSkillProfileProvider} to build a {@link io.casehub.work.api.SkillProfile}.
 *
 * <p>
 * Not a FK to any user table — decoupled from identity management.
 */
@Entity
@Table(name = "worker_skill_profile")
public class WorkerSkillProfile extends PanacheEntityBase {

    @Id
    @Column(name = "worker_id", nullable = false)
    public String workerId;

    @Column(name = "narrative", columnDefinition = "TEXT")
    public String narrative;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    @PrePersist
    public void onPrePersist() {
        final Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    public void onPreUpdate() {
        updatedAt = Instant.now();
    }
}
