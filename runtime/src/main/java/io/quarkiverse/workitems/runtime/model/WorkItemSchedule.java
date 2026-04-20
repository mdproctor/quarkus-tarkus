package io.quarkiverse.workitems.runtime.model;

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
 * A recurring WorkItem creation schedule, linking a {@link WorkItemTemplate}
 * to a Quartz cron expression.
 *
 * <h2>How it works</h2>
 * <p>
 * A background job ({@link io.quarkiverse.workitems.runtime.service.WorkItemScheduleService})
 * runs every minute, finds all active schedules where {@code nextFireAt ≤ now},
 * instantiates the linked template, updates {@code lastFiredAt}, and computes
 * the next {@code nextFireAt} from the cron expression.
 *
 * <h2>Cron format</h2>
 * <p>
 * Quartz cron format — 6 fields: {@code sec min hour day month weekday}
 * <ul>
 * <li>{@code 0 0 9 * * ?} — every day at 09:00</li>
 * <li>{@code 0 0 9 ? * MON-FRI} — every weekday at 09:00</li>
 * <li>{@code 0 0/30 * * * ?} — every 30 minutes</li>
 * <li>{@code 0 0 0 1 * ?} — first of every month at midnight</li>
 * </ul>
 *
 * <h2>Restart safety</h2>
 * <p>
 * {@code nextFireAt} is persisted in the database. After a JVM restart, the
 * background job reads the stored value and fires any overdue schedules — no
 * events are missed, regardless of how long the application was down.
 */
@Entity
@Table(name = "work_item_schedule")
public class WorkItemSchedule extends PanacheEntityBase {

    @Id
    public UUID id;

    /** Human-readable name for this schedule. */
    @Column(nullable = false, length = 255)
    public String name;

    /** The template to instantiate on each firing. Cascade-deleted with the template. */
    @Column(name = "template_id", nullable = false)
    public UUID templateId;

    /**
     * Quartz cron expression defining the firing schedule.
     * See class Javadoc for format and examples.
     */
    @Column(name = "cron_expression", nullable = false, length = 255)
    public String cronExpression;

    /** When false, the schedule is paused and will not fire. */
    @Column(nullable = false)
    public boolean active = true;

    /** Who created this schedule. */
    @Column(name = "created_by", nullable = false, length = 255)
    public String createdBy;

    /** When this schedule was created. */
    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    /** When this schedule last fired; null if it has never fired. */
    @Column(name = "last_fired_at")
    public Instant lastFiredAt;

    /**
     * The next time this schedule will fire, computed from {@link #cronExpression}.
     * The background job finds all active schedules where this is in the past.
     */
    @Column(name = "next_fire_at")
    public Instant nextFireAt;

    @PrePersist
    void prePersist() {
        if (id == null)
            id = UUID.randomUUID();
        if (createdAt == null)
            createdAt = Instant.now();
    }

    /** All active schedules whose nextFireAt is on or before {@code now}. */
    public static List<WorkItemSchedule> findDue(final Instant now) {
        return list("active = true AND nextFireAt IS NOT NULL AND nextFireAt <= ?1 ORDER BY nextFireAt ASC", now);
    }

    /** All schedules ordered by name. */
    public static List<WorkItemSchedule> listAllByName() {
        return list("ORDER BY name ASC");
    }
}
