package io.quarkiverse.work.reports.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import io.quarkiverse.work.runtime.model.WorkItemPriority;
import io.quarkus.cache.CacheResult;

@ApplicationScoped
public class ReportService {

    @Inject
    EntityManager em;

    @CacheResult(cacheName = "reports")
    public SlaBreachReport slaBreaches(Instant from, Instant to, String category, WorkItemPriority priority) {
        return new SlaBreachReport(List.of(), new SlaSummary(0, 0.0, Map.of()));
    }

    @CacheResult(cacheName = "reports")
    public ActorReport actorPerformance(String actorId, Instant from, Instant to, String category) {
        return new ActorReport(actorId, 0, 0, 0, null, Map.of());
    }

    @CacheResult(cacheName = "reports")
    public ThroughputReport throughput(Instant from, Instant to, String groupBy) {
        return new ThroughputReport(from, to, groupBy, List.of());
    }

    @CacheResult(cacheName = "reports")
    public QueueHealthReport queueHealth(String category, WorkItemPriority priority) {
        return new QueueHealthReport(Instant.now(), 0, 0, 0, null, 0);
    }
}
