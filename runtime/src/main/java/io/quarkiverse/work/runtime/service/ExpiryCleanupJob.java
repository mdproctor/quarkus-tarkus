package io.quarkiverse.work.runtime.service;

import java.time.Instant;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import io.quarkiverse.work.api.EscalationPolicy;
import io.quarkiverse.work.runtime.event.WorkItemLifecycleEvent;
import io.quarkiverse.work.runtime.model.AuditEntry;
import io.quarkiverse.work.runtime.model.WorkItem;
import io.quarkiverse.work.runtime.model.WorkItemStatus;
import io.quarkiverse.work.runtime.repository.AuditEntryStore;
import io.quarkiverse.work.runtime.repository.WorkItemQuery;
import io.quarkiverse.work.runtime.repository.WorkItemStore;
import io.quarkus.scheduler.Scheduled;

@ApplicationScoped
public class ExpiryCleanupJob {

    @Inject
    WorkItemStore workItemStore;

    @Inject
    AuditEntryStore auditStore;

    @Inject
    @ExpiryEscalation
    EscalationPolicy escalationPolicy;

    @Inject
    Event<WorkItemLifecycleEvent> lifecycleEvent;

    @Scheduled(every = "${quarkus.work.cleanup.expiry-check-seconds}s")
    @Transactional
    public void checkExpired() {
        final Instant now = Instant.now();
        final List<WorkItem> expired = workItemStore.scan(WorkItemQuery.expired(now));
        for (final WorkItem item : expired) {
            item.status = WorkItemStatus.EXPIRED;
            item.completedAt = now;
            workItemStore.put(item);

            final AuditEntry entry = new AuditEntry();
            entry.workItemId = item.id;
            entry.event = "EXPIRED";
            entry.actor = "system";
            entry.occurredAt = now;
            auditStore.append(entry);
            lifecycleEvent.fire(WorkItemLifecycleEvent.of("EXPIRED", item, "system", null));

            final WorkItemLifecycleEvent escalatedEvent = WorkItemLifecycleEvent.of("ESCALATED", item, "system", null);
            escalationPolicy.escalate(escalatedEvent);
            lifecycleEvent.fire(escalatedEvent);
        }
    }
}
