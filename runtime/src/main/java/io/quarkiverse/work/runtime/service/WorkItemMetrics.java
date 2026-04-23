package io.quarkiverse.work.runtime.service;

import java.time.Instant;
import java.util.List;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkiverse.work.runtime.event.WorkItemLifecycleEvent;
import io.quarkiverse.work.runtime.model.WorkItem;
import io.quarkiverse.work.runtime.model.WorkItemStatus;
import io.quarkiverse.work.runtime.repository.WorkItemStore;
import io.quarkus.runtime.Startup;

/**
 * Registers Micrometer meters for WorkItem operational observability.
 *
 * <h2>Gauges (DB-backed, sampled at scrape time)</h2>
 * <ul>
 * <li>{@code workitems.active} — all non-terminal WorkItems</li>
 * <li>{@code workitems.by.status{status=...}} — count per active status</li>
 * <li>{@code workitems.overdue} — non-terminal items past {@code expiresAt}</li>
 * <li>{@code workitems.claim.deadline.breached} — PENDING items past {@code claimDeadline}</li>
 * </ul>
 *
 * <h2>Counters (event-driven, cumulative)</h2>
 * <ul>
 * <li>{@code workitems.lifecycle.events{type=...}} — incremented on every
 * {@link WorkItemLifecycleEvent}; the {@code type} tag is the event name suffix</li>
 * </ul>
 *
 * <h2>Registry</h2>
 * <p>
 * Registers against the application's {@link MeterRegistry}. The registry implementation
 * (Prometheus, Datadog, CloudWatch, etc.) is chosen by the consuming application.
 * Add {@code io.quarkus:quarkus-micrometer-registry-prometheus} to expose
 * {@code GET /q/metrics} in Prometheus format.
 *
 * <h2>Gauge sampling and transactions</h2>
 * <p>
 * Gauge functions are annotated {@link Transactional} so the CDI proxy wraps each
 * scrape call in a read transaction — JPA queries work correctly from the Prometheus
 * scrape thread. Gauges query the store at scrape time with no caching.
 */
@ApplicationScoped
@Startup // force eager initialization so gauges are registered before the first scrape
public class WorkItemMetrics {

    @Inject
    MeterRegistry registry;

    @Inject
    WorkItemStore workItemStore;

    @PostConstruct
    void registerMeters() {
        // "workitems.active" avoids the _total suffix conflict with Prometheus counters
        Gauge.builder("workitems.active", this, WorkItemMetrics::activeCount)
                .description("Total non-terminal WorkItems")
                .register(registry);

        for (final WorkItemStatus status : WorkItemStatus.values()) {
            if (!status.isTerminal()) {
                final WorkItemStatus s = status;
                Gauge.builder("workitems.by.status", this, m -> m.byStatus(s))
                        .description("WorkItems per active status")
                        .tag("status", status.name())
                        .register(registry);
            }
        }

        Gauge.builder("workitems.overdue", this, WorkItemMetrics::overdueCount)
                .description("Non-terminal WorkItems past their completion deadline")
                .register(registry);

        Gauge.builder("workitems.claim.deadline.breached", this, WorkItemMetrics::claimBreachedCount)
                .description("PENDING WorkItems past their claim deadline")
                .register(registry);
    }

    /** Increments the lifecycle event counter on each WorkItem transition. */
    public void onLifecycleEvent(@Observes final WorkItemLifecycleEvent event) {
        if (event.type() == null)
            return;
        final String suffix = event.type().substring(event.type().lastIndexOf('.') + 1);
        Counter.builder("workitems.lifecycle.events")
                .description("WorkItem lifecycle event count")
                .tag("type", suffix)
                .register(registry)
                .increment();
    }

    // ── Gauge functions — public + @Transactional so CDI proxy provides a JPA context ──

    /** Called by the gauge lambda at scrape time — @Transactional ensures JPA works. */
    @Transactional
    public double activeCount() {
        return countNonTerminal(workItemStore.scanAll());
    }

    @Transactional
    public double byStatus(final WorkItemStatus status) {
        return countByStatus(workItemStore.scanAll(), status);
    }

    @Transactional
    public double overdueCount() {
        return countOverdue(workItemStore.scanAll(), Instant.now());
    }

    @Transactional
    public double claimBreachedCount() {
        return countClaimDeadlineBreached(workItemStore.scanAll(), Instant.now());
    }

    // ── Pure static helpers — unit-testable without CDI or DB ────────────────

    /** Count all non-terminal WorkItems. */
    public static long countNonTerminal(final List<WorkItem> items) {
        return items.stream()
                .filter(wi -> wi.status != null && !wi.status.isTerminal())
                .count();
    }

    /** Count WorkItems in a specific status. */
    public static long countByStatus(final List<WorkItem> items, final WorkItemStatus status) {
        return items.stream().filter(wi -> status == wi.status).count();
    }

    /** Count non-terminal WorkItems past their {@code expiresAt}. */
    public static long countOverdue(final List<WorkItem> items, final Instant now) {
        return items.stream()
                .filter(wi -> wi.status != null && !wi.status.isTerminal())
                .filter(wi -> wi.expiresAt != null && wi.expiresAt.isBefore(now))
                .count();
    }

    /** Count PENDING WorkItems past their {@code claimDeadline}. */
    public static long countClaimDeadlineBreached(final List<WorkItem> items, final Instant now) {
        return items.stream()
                .filter(wi -> wi.status == WorkItemStatus.PENDING)
                .filter(wi -> wi.claimDeadline != null && wi.claimDeadline.isBefore(now))
                .count();
    }
}
