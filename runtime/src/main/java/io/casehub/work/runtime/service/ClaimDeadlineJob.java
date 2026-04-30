package io.casehub.work.runtime.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import io.casehub.work.api.ClaimSlaContext;
import io.casehub.work.api.ClaimSlaPolicy;
import io.casehub.work.api.EscalationPolicy;
import io.casehub.work.runtime.config.WorkItemsConfig;
import io.casehub.work.runtime.event.WorkItemLifecycleEvent;
import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.runtime.repository.WorkItemQuery;
import io.casehub.work.runtime.repository.WorkItemStore;
import io.quarkus.scheduler.Scheduled;

@ApplicationScoped
public class ClaimDeadlineJob {

    @Inject
    WorkItemStore workItemStore;

    @Inject
    @ClaimEscalation
    EscalationPolicy claimEscalationPolicy;

    @Inject
    Event<WorkItemLifecycleEvent> lifecycleEvent;

    @Inject
    ClaimSlaPolicy claimSlaPolicy;

    @Inject
    WorkItemsConfig config;

    @Scheduled(every = "${casehub.work.cleanup.expiry-check-seconds}s")
    @Transactional
    public void checkUnclaimedPastDeadline() {
        final Instant now = Instant.now();
        final List<WorkItem> unclaimed = workItemStore.scan(WorkItemQuery.claimExpired(now));
        for (final WorkItem item : unclaimed) {
            // Accumulate time this pool phase spent unclaimed before resetting the deadline
            if (item.lastReturnedToPoolAt != null) {
                item.accumulatedUnclaimedSeconds += Duration.between(item.lastReturnedToPoolAt, now).toSeconds();
            }
            // Begin a new pool phase; let ClaimSlaPolicy decide the next deadline
            item.lastReturnedToPoolAt = now;
            item.claimDeadline = claimSlaPolicy.computePoolDeadline(buildContext(item, now));
            workItemStore.put(item);

            final WorkItemLifecycleEvent claimExpiredEvent = WorkItemLifecycleEvent.of("CLAIM_EXPIRED", item, "system", null);
            claimEscalationPolicy.escalate(claimExpiredEvent);
            lifecycleEvent.fire(claimExpiredEvent);
        }
    }

    private ClaimSlaContext buildContext(final WorkItem item, final Instant now) {
        final Duration totalPoolSla = config.defaultClaimHours() > 0
                ? Duration.ofHours(config.defaultClaimHours())
                : Duration.ofHours(24);
        final Instant submitted = item.createdAt != null ? item.createdAt : now;
        return new ClaimSlaContext(submitted, totalPoolSla,
                Duration.ofSeconds(item.accumulatedUnclaimedSeconds), now);
    }
}
