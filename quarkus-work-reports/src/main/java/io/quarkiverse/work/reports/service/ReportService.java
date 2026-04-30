package io.casehub.work.reports.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.runtime.model.WorkItemPriority;
import io.casehub.work.runtime.model.WorkItemStatus;
import io.quarkus.cache.CacheResult;

@ApplicationScoped
public class ReportService {

    @Inject
    EntityManager em;

    @CacheResult(cacheName = "reports")
    public SlaBreachReport slaBreaches(final Instant from, final Instant to,
            final String category, final WorkItemPriority priority) {
        final Instant now = Instant.now();

        final StringBuilder jpql = new StringBuilder(
                "SELECT w FROM WorkItem w WHERE w.expiresAt IS NOT NULL AND (")
                .append("(w.status IN :activeStatuses AND w.expiresAt < :now)")
                .append(" OR ")
                .append("(w.status IN :terminalStatuses")
                .append(" AND w.completedAt IS NOT NULL AND w.completedAt > w.expiresAt))");

        if (from != null) {
            jpql.append(" AND w.expiresAt >= :from");
        }
        if (to != null) {
            jpql.append(" AND w.expiresAt <= :to");
        }
        if (category != null && !category.isBlank()) {
            jpql.append(" AND w.category = :category");
        }
        if (priority != null) {
            jpql.append(" AND w.priority = :priority");
        }
        jpql.append(" ORDER BY w.expiresAt DESC");

        final TypedQuery<WorkItem> q = em.createQuery(jpql.toString(), WorkItem.class);
        q.setHint("jakarta.persistence.query.timeout", 30_000);
        q.setParameter("now", now);
        q.setParameter("activeStatuses", List.of(
                WorkItemStatus.PENDING, WorkItemStatus.ASSIGNED, WorkItemStatus.IN_PROGRESS,
                WorkItemStatus.SUSPENDED, WorkItemStatus.ESCALATED, WorkItemStatus.EXPIRED));
        q.setParameter("terminalStatuses", List.of(
                WorkItemStatus.COMPLETED, WorkItemStatus.REJECTED, WorkItemStatus.CANCELLED));
        if (from != null) {
            q.setParameter("from", from);
        }
        if (to != null) {
            q.setParameter("to", to);
        }
        if (category != null && !category.isBlank()) {
            q.setParameter("category", category);
        }
        if (priority != null) {
            q.setParameter("priority", priority);
        }

        final List<WorkItem> breached = q.getResultList();

        final List<SlaBreachItem> items = new ArrayList<>();
        final Map<String, Long> byCategory = new LinkedHashMap<>();
        long totalMinutes = 0;

        for (final WorkItem wi : breached) {
            final Instant end = wi.completedAt != null ? wi.completedAt : now;
            final long mins = Math.max(0, ChronoUnit.MINUTES.between(wi.expiresAt, end));
            items.add(new SlaBreachItem(
                    wi.id.toString(),
                    wi.category,
                    wi.priority != null ? wi.priority.name() : null,
                    wi.expiresAt,
                    wi.completedAt,
                    wi.status.name(),
                    mins));
            totalMinutes += mins;
            if (wi.category != null) {
                byCategory.merge(wi.category, 1L, Long::sum);
            }
        }

        final double avg = breached.isEmpty() ? 0.0 : (double) totalMinutes / breached.size();
        return new SlaBreachReport(items,
                new SlaSummary(breached.size(), Math.round(avg * 10.0) / 10.0, byCategory));
    }

    @CacheResult(cacheName = "reports")
    public ActorReport actorPerformance(final String actorId, final Instant from,
            final Instant to, final String category) {

        final long totalAssigned = countAuditEvents(actorId, "ASSIGNED", from, to, category);
        final long totalCompleted = countAuditEvents(actorId, "COMPLETED", from, to, category);
        final long totalRejected = countAuditEvents(actorId, "REJECTED", from, to, category);

        // avg(completedAt - assignedAt) for items completed by this actor
        final StringBuilder avgJpql = new StringBuilder(
                "SELECT w.assignedAt, w.completedAt FROM WorkItem w"
                        + " WHERE w.assigneeId = :actorId"
                        + " AND w.completedAt IS NOT NULL AND w.assignedAt IS NOT NULL");
        if (from != null) {
            avgJpql.append(" AND w.completedAt >= :from");
        }
        if (to != null) {
            avgJpql.append(" AND w.completedAt <= :to");
        }
        if (category != null && !category.isBlank()) {
            avgJpql.append(" AND w.category = :category");
        }

        final TypedQuery<Object[]> avgQ = em.createQuery(avgJpql.toString(), Object[].class);
        avgQ.setParameter("actorId", actorId);
        if (from != null) {
            avgQ.setParameter("from", from);
        }
        if (to != null) {
            avgQ.setParameter("to", to);
        }
        if (category != null && !category.isBlank()) {
            avgQ.setParameter("category", category);
        }

        final List<Object[]> rows = avgQ.getResultList();
        final Double avgCompletionMinutes = rows.isEmpty() ? null
                : rows.stream()
                        .mapToLong(r -> ChronoUnit.MINUTES.between((Instant) r[0], (Instant) r[1]))
                        .average()
                        .stream()
                        .boxed()
                        .map(d -> Math.round(d * 10.0) / 10.0)
                        .findFirst()
                        .orElse(null);

        // byCategory via GROUP BY — no N+1
        final StringBuilder catJpql = new StringBuilder(
                "SELECT w.category, COUNT(w) FROM WorkItem w"
                        + " WHERE w.assigneeId = :actorId"
                        + " AND w.status = :completed");
        if (from != null) {
            catJpql.append(" AND w.completedAt >= :from");
        }
        if (to != null) {
            catJpql.append(" AND w.completedAt <= :to");
        }
        if (category != null && !category.isBlank()) {
            catJpql.append(" AND w.category = :category");
        }
        catJpql.append(" GROUP BY w.category");

        final TypedQuery<Object[]> catQ = em.createQuery(catJpql.toString(), Object[].class);
        catQ.setParameter("actorId", actorId);
        catQ.setParameter("completed", WorkItemStatus.COMPLETED);
        if (from != null) {
            catQ.setParameter("from", from);
        }
        if (to != null) {
            catQ.setParameter("to", to);
        }
        if (category != null && !category.isBlank()) {
            catQ.setParameter("category", category);
        }

        final Map<String, Long> byCategory = new LinkedHashMap<>();
        for (final Object[] row : catQ.getResultList()) {
            if (row[0] != null) {
                byCategory.put((String) row[0], (Long) row[1]);
            }
        }

        return new ActorReport(actorId, totalAssigned, totalCompleted, totalRejected,
                avgCompletionMinutes, byCategory);
    }

    @CacheResult(cacheName = "reports")
    public ThroughputReport throughput(final Instant from, final Instant to, final String groupBy) {
        final List<Object[]> dayCreated = queryDayBuckets("createdAt", from, to, null);
        final List<Object[]> dayCompleted = queryDayBucketsCompleted(from, to);
        final List<ThroughputBucket> buckets = ThroughputBucketAggregator.aggregate(dayCreated, dayCompleted, groupBy);
        return new ThroughputReport(from, to, groupBy, buckets);
    }

    @CacheResult(cacheName = "reports")
    public QueueHealthReport queueHealth(final String category, final WorkItemPriority priority) {
        final Instant now = Instant.now();
        final List<WorkItemStatus> activeStatuses = List.of(
                WorkItemStatus.PENDING, WorkItemStatus.ASSIGNED, WorkItemStatus.IN_PROGRESS,
                WorkItemStatus.SUSPENDED, WorkItemStatus.ESCALATED, WorkItemStatus.EXPIRED);

        // overdueCount
        final StringBuilder overdueJpql = new StringBuilder(
                "SELECT COUNT(w) FROM WorkItem w WHERE w.expiresAt IS NOT NULL"
                        + " AND w.expiresAt < :now AND w.status IN :activeStatuses");
        if (category != null && !category.isBlank()) {
            overdueJpql.append(" AND w.category = :category");
        }
        if (priority != null) {
            overdueJpql.append(" AND w.priority = :priority");
        }

        final TypedQuery<Long> overdueQ = em.createQuery(overdueJpql.toString(), Long.class);
        overdueQ.setParameter("now", now);
        overdueQ.setParameter("activeStatuses", activeStatuses);
        if (category != null && !category.isBlank()) {
            overdueQ.setParameter("category", category);
        }
        if (priority != null) {
            overdueQ.setParameter("priority", priority);
        }
        final long overdueCount = overdueQ.getSingleResult();

        // criticalOverdueCount
        final StringBuilder critJpql = new StringBuilder(
                "SELECT COUNT(w) FROM WorkItem w WHERE w.expiresAt IS NOT NULL"
                        + " AND w.expiresAt < :now AND w.status IN :activeStatuses"
                        + " AND w.priority = :critical");
        if (category != null && !category.isBlank()) {
            critJpql.append(" AND w.category = :category");
        }

        final TypedQuery<Long> critQ = em.createQuery(critJpql.toString(), Long.class);
        critQ.setParameter("now", now);
        critQ.setParameter("activeStatuses", activeStatuses);
        critQ.setParameter("critical", WorkItemPriority.CRITICAL);
        if (category != null && !category.isBlank()) {
            critQ.setParameter("category", category);
        }
        final long criticalOverdueCount = critQ.getSingleResult();

        // pending items: count, oldest, avg age
        final StringBuilder pendingJpql = new StringBuilder(
                "SELECT w.createdAt FROM WorkItem w WHERE w.status = :pending");
        if (category != null && !category.isBlank()) {
            pendingJpql.append(" AND w.category = :category");
        }
        if (priority != null) {
            pendingJpql.append(" AND w.priority = :priority");
        }

        final TypedQuery<Instant> pendingQ = em.createQuery(pendingJpql.toString(), Instant.class);
        pendingQ.setParameter("pending", WorkItemStatus.PENDING);
        if (category != null && !category.isBlank()) {
            pendingQ.setParameter("category", category);
        }
        if (priority != null) {
            pendingQ.setParameter("priority", priority);
        }

        final List<Instant> pendingCreatedAts = pendingQ.getResultList();
        final long pendingCount = pendingCreatedAts.size();
        final Instant oldestUnclaimed = pendingCreatedAts.stream().min(Instant::compareTo).orElse(null);
        final long avgPendingAgeSeconds = pendingCreatedAts.isEmpty() ? 0L
                : (long) pendingCreatedAts.stream()
                        .mapToLong(c -> ChronoUnit.SECONDS.between(c, now))
                        .average().orElse(0.0);

        return new QueueHealthReport(now, overdueCount, pendingCount,
                avgPendingAgeSeconds, oldestUnclaimed, criticalOverdueCount);
    }

    private long countAuditEvents(final String actorId, final String event,
            final Instant from, final Instant to, final String category) {
        final StringBuilder jpql = new StringBuilder(
                "SELECT COUNT(a) FROM AuditEntry a WHERE a.actor = :actorId AND a.event = :event");
        if (from != null) {
            jpql.append(" AND a.occurredAt >= :from");
        }
        if (to != null) {
            jpql.append(" AND a.occurredAt <= :to");
        }
        if (category != null && !category.isBlank()) {
            jpql.append(" AND a.workItemId IN"
                    + " (SELECT w.id FROM WorkItem w WHERE w.category = :category)");
        }

        final TypedQuery<Long> q = em.createQuery(jpql.toString(), Long.class);
        q.setParameter("actorId", actorId);
        q.setParameter("event", event);
        if (from != null) {
            q.setParameter("from", from);
        }
        if (to != null) {
            q.setParameter("to", to);
        }
        if (category != null && !category.isBlank()) {
            q.setParameter("category", category);
        }
        return q.getSingleResult();
    }

    @SuppressWarnings("unchecked")
    private List<Object[]> queryDayBuckets(final String field, final Instant from,
            final Instant to, final WorkItemStatus status) {
        final String jpql = "SELECT CAST(date_trunc('day', w." + field + ") AS LocalDate), COUNT(w)"
                + " FROM WorkItem w"
                + " WHERE w." + field + " >= :from AND w." + field + " <= :to"
                + (status != null ? " AND w.status = :status" : "")
                + " GROUP BY CAST(date_trunc('day', w." + field + ") AS LocalDate)"
                + " ORDER BY CAST(date_trunc('day', w." + field + ") AS LocalDate)";

        final TypedQuery<Object[]> q = em.createQuery(jpql, Object[].class);
        q.setHint("jakarta.persistence.query.timeout", 30_000);
        q.setParameter("from", from);
        q.setParameter("to", to);
        if (status != null) {
            q.setParameter("status", status);
        }
        return q.getResultList();
    }

    private List<Object[]> queryDayBucketsCompleted(final Instant from, final Instant to) {
        final String jpql = "SELECT CAST(date_trunc('day', w.completedAt) AS LocalDate), COUNT(w)"
                + " FROM WorkItem w"
                + " WHERE w.completedAt >= :from AND w.completedAt <= :to"
                + " AND w.status IN :terminalStatuses"
                + " GROUP BY CAST(date_trunc('day', w.completedAt) AS LocalDate)"
                + " ORDER BY CAST(date_trunc('day', w.completedAt) AS LocalDate)";

        final TypedQuery<Object[]> q = em.createQuery(jpql, Object[].class);
        q.setHint("jakarta.persistence.query.timeout", 30_000);
        q.setParameter("from", from);
        q.setParameter("to", to);
        q.setParameter("terminalStatuses", List.of(
                WorkItemStatus.COMPLETED, WorkItemStatus.REJECTED,
                WorkItemStatus.CANCELLED, WorkItemStatus.ESCALATED));
        return q.getResultList();
    }
}
