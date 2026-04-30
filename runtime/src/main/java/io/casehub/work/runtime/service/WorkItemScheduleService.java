package io.casehub.work.runtime.service;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;
import org.quartz.CronExpression;

import io.casehub.work.runtime.model.WorkItemSchedule;
import io.casehub.work.runtime.model.WorkItemTemplate;
import io.quarkus.scheduler.Scheduled;

/**
 * Manages {@link WorkItemSchedule} records and drives periodic firing.
 *
 * <h2>Background job</h2>
 * <p>
 * {@link #processSchedules()} runs every minute (configurable via
 * {@code casehub.work.scheduler.schedule-check-interval}). It finds all
 * active schedules where {@code nextFireAt ≤ now}, instantiates the linked
 * template, updates {@code lastFiredAt}, and computes the new {@code nextFireAt}.
 *
 * <h2>Restart safety</h2>
 * <p>
 * {@code nextFireAt} is persisted. Schedules that fired while the application
 * was down are caught on the next job run — no events are silently missed.
 *
 * <h2>Pure static helpers</h2>
 * <p>
 * {@link #computeNextFireAt(String)} and {@link #isDue(Instant, Instant)} are
 * public static methods for unit testability without CDI or a database.
 */
@ApplicationScoped
public class WorkItemScheduleService {

    private static final Logger LOG = Logger.getLogger(WorkItemScheduleService.class);

    @Inject
    WorkItemTemplateService templateService;

    /**
     * Background job: find all due active schedules and instantiate their templates.
     *
     * <p>
     * Runs every minute by default. Each overdue schedule fires exactly once per
     * invocation — there is no catch-up loop if the application was down for many
     * cron periods.
     *
     * @return the number of WorkItems created this run (useful in tests for direct invocation)
     */
    /** Scheduler trigger — must return void. Delegates to {@link #processSchedules()}. */
    @Scheduled(identity = "work-item-schedule-check", every = "${casehub.work.scheduler.schedule-check-interval:60}s", delayed = "10s")
    public void scheduledCheck() {
        processSchedules();
    }

    /**
     * Find all due active schedules and fire each in its own transaction.
     *
     * <p>
     * <strong>Cluster safety:</strong> each schedule fires in a
     * {@code REQUIRES_NEW} transaction via {@link #fireSchedule}. If two Quarkus
     * nodes both pick up the same due schedule, the first to commit wins (version
     * incremented). The second's {@code UPDATE WHERE version=N} matches zero rows →
     * {@code OptimisticLockException} → that node's {@code REQUIRES_NEW}
     * rolls back cleanly. Exactly one WorkItem is created per schedule per interval.
     *
     * @return the number of WorkItems successfully created this run
     */
    @Transactional(Transactional.TxType.NOT_SUPPORTED)
    public int processSchedules() {
        final List<WorkItemSchedule> due = findDueSchedules();
        int created = 0;
        for (final WorkItemSchedule schedule : due) {
            try {
                fireSchedule(schedule.id);
                created++;
            } catch (jakarta.persistence.OptimisticLockException e) {
                LOG.infof("Schedule %s already fired by another node — skipping", schedule.id);
            } catch (Exception e) {
                LOG.errorf("Failed to fire schedule %s: %s", schedule.id, e.getMessage());
            }
        }
        return created;
    }

    /** Read the due schedules in a short-lived transaction. */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public List<WorkItemSchedule> findDueSchedules() {
        return WorkItemSchedule.findDue(Instant.now());
    }

    /**
     * Fire a single schedule in its own transaction.
     *
     * <p>
     * {@code REQUIRES_NEW} ensures this transaction is independent of any outer
     * transaction. If two nodes race on the same schedule, the second to commit
     * will hit the {@code @Version} check, throw {@code OptimisticLockException},
     * and roll back without creating a duplicate WorkItem.
     *
     * @param scheduleId the schedule to fire
     * @throws jakarta.persistence.OptimisticLockException if another node fired first
     * @throws Exception if template is missing or instantiation fails
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void fireSchedule(final UUID scheduleId) throws Exception {
        final WorkItemSchedule schedule = WorkItemSchedule.findById(scheduleId);
        if (schedule == null)
            return;

        final WorkItemTemplate template = WorkItemTemplate.findById(schedule.templateId);
        if (template == null) {
            LOG.warnf("Schedule %s references missing template %s — skipping", scheduleId, schedule.templateId);
            return;
        }

        final Instant now = Instant.now();
        templateService.instantiate(template, null, null, "schedule:" + scheduleId);
        schedule.lastFiredAt = now;
        schedule.nextFireAt = computeNextFireAt(schedule.cronExpression);
        // flush here — if another node already committed, @Version mismatch → OLE → REQUIRES_NEW rolls back
    }

    /**
     * Create a new schedule.
     *
     * @param name human-readable schedule name
     * @param templateId the template to instantiate on each firing
     * @param cronExpression Quartz cron expression (6 fields)
     * @param createdBy the actor creating this schedule
     * @return the persisted schedule with {@code nextFireAt} pre-computed
     * @throws Exception if the cron expression is invalid
     */
    @Transactional
    public WorkItemSchedule create(final String name, final UUID templateId,
            final String cronExpression, final String createdBy) throws Exception {
        final Instant nextFireAt = computeNextFireAt(cronExpression); // validates the expression

        final WorkItemSchedule s = new WorkItemSchedule();
        s.name = name;
        s.templateId = templateId;
        s.cronExpression = cronExpression;
        s.createdBy = createdBy;
        s.active = true;
        s.nextFireAt = nextFireAt;
        s.persist();
        return s;
    }

    /**
     * Enable or disable a schedule.
     * Re-enabling recomputes {@code nextFireAt} so the schedule doesn't fire
     * immediately for all missed periods while it was inactive.
     */
    @Transactional
    public Optional<WorkItemSchedule> setActive(final UUID id, final boolean active) throws Exception {
        final WorkItemSchedule s = WorkItemSchedule.findById(id);
        if (s == null)
            return Optional.empty();
        s.active = active;
        if (active) {
            s.nextFireAt = computeNextFireAt(s.cronExpression); // reset; don't catch up
        }
        return Optional.of(s);
    }

    /** Find a schedule by ID. */
    @Transactional
    public Optional<WorkItemSchedule> findById(final UUID id) {
        return Optional.ofNullable(WorkItemSchedule.findById(id));
    }

    /**
     * Force a schedule's {@code nextFireAt} to be in the past so
     * {@link #processSchedules()} will fire it on the next invocation.
     * <strong>Test use only.</strong>
     */
    @Transactional
    public void forceDue(final UUID scheduleId) {
        final WorkItemSchedule s = WorkItemSchedule.findById(scheduleId);
        if (s != null) {
            s.nextFireAt = Instant.now().minusSeconds(1);
        }
    }

    // ── Pure static helpers — unit-testable without CDI ───────────────────────

    /**
     * Compute the next fire instant from a Quartz cron expression.
     *
     * @param cronExpression a valid Quartz cron expression (6 fields)
     * @return the next scheduled instant after now
     * @throws Exception if the cron expression is syntactically invalid
     */
    public static Instant computeNextFireAt(final String cronExpression) throws Exception {
        final CronExpression expr = new CronExpression(cronExpression);
        final Date next = expr.getNextValidTimeAfter(new Date());
        return next != null ? next.toInstant() : null;
    }

    /**
     * Returns {@code true} if the schedule is due to fire — its {@code nextFireAt}
     * is non-null and on or before {@code now}.
     *
     * @param nextFireAt the schedule's stored next fire time; may be null
     * @param now the reference instant
     * @return whether the schedule should fire
     */
    public static boolean isDue(final Instant nextFireAt, final Instant now) {
        return nextFireAt != null && !nextFireAt.isAfter(now);
    }
}
