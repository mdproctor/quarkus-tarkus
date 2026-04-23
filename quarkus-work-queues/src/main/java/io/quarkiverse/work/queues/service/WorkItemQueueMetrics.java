package io.quarkiverse.work.queues.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkiverse.work.queues.event.WorkItemQueueEvent;
import io.quarkiverse.work.queues.model.QueueView;
import io.quarkiverse.work.runtime.repository.WorkItemQuery;
import io.quarkiverse.work.runtime.repository.WorkItemStore;

/**
 * Registers per-queue depth gauges when queue membership changes.
 *
 * <p>
 * Rather than polling all queues at startup, this bean registers a new
 * {@code workitems.queue.depth} gauge the first time a {@link WorkItemQueueEvent}
 * is observed for each queue — lazy registration means only queues that have
 * actually received WorkItems appear in the metrics.
 *
 * <h2>Metric</h2>
 *
 * <pre>
 *   workitems.queue.depth{queue="Legal Review Queue"} 5.0
 *   workitems.queue.depth{queue="Finance Approval Queue"} 2.0
 * </pre>
 *
 * <p>
 * The gauge queries {@link WorkItemStore} at scrape time using
 * {@link WorkItemQuery#byLabelPattern} matching the queue's label pattern.
 */
@ApplicationScoped
public class WorkItemQueueMetrics {

    @Inject
    MeterRegistry registry;

    @Inject
    WorkItemStore workItemStore;

    /**
     * Observes queue events and lazily registers a depth gauge for each new queue.
     * Micrometer deduplicates registrations — calling this multiple times for the
     * same queue name is safe.
     */
    public void onQueueEvent(@Observes final WorkItemQueueEvent event) {
        final QueueView queue = QueueView.findById(event.queueViewId());
        if (queue == null)
            return;

        Gauge.builder("workitems.queue.depth", workItemStore,
                store -> store.scan(WorkItemQuery.byLabelPattern(queue.labelPattern)).size())
                .description("Number of WorkItems currently in this queue")
                .tag("queue", queue.name)
                .register(registry);
    }
}
