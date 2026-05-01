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
import io.casehub.work.runtime.model.AuditEntry;
import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.runtime.model.WorkItemStatus;
import io.casehub.work.runtime.repository.AuditEntryStore;
import io.casehub.work.runtime.repository.WorkItemQuery;
import io.casehub.work.runtime.repository.WorkItemStore;

/**
 * Handles expiry evaluation, escalation dispatch, and claim deadline breach processing.
 * Called by {@link ExpiryCleanupJob} and {@link ClaimDeadlineJob}; also provides
 * {@link #computeNewClaimDeadline} for use by lifecycle transitions that return a
 * WorkItem to the pool (release, delegate).
 */
@ApplicationScoped
public class ExpiryLifecycleService {

    @Inject
    WorkItemStore workItemStore;

    @Inject
    AuditEntryStore auditStore;

    @Inject
    @ExpiryEscalation
    EscalationPolicy escalationPolicy;

    @Inject
    @ClaimEscalation
    EscalationPolicy claimEscalationPolicy;

    @Inject
    Event<WorkItemLifecycleEvent> lifecycleEvent;

    @Inject
    ClaimSlaPolicy claimSlaPolicy;

    @Inject
    WorkItemsConfig config;

    /**
     * Marks all WorkItems whose {@code expiresAt} has passed as EXPIRED and fires escalation.
     * Called by {@link ExpiryCleanupJob} on each scheduled tick.
     */
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
            if (lifecycleEvent != null) {
                lifecycleEvent.fire(WorkItemLifecycleEvent.of("EXPIRED", item, "system", null));
            }
            final WorkItemLifecycleEvent escalatedEvent = WorkItemLifecycleEvent.of("ESCALATED", item, "system", null);
            escalationPolicy.escalate(escalatedEvent);
            if (lifecycleEvent != null) {
                lifecycleEvent.fire(escalatedEvent);
            }
        }
    }

    /**
     * Processes WorkItems whose {@code claimDeadline} has passed — accumulates unclaimed time,
     * resets the deadline via {@link ClaimSlaPolicy}, and fires escalation.
     * Called by {@link ClaimDeadlineJob} on each scheduled tick.
     */
    @Transactional
    public void checkClaimDeadlines() {
        final Instant now = Instant.now();
        final List<WorkItem> unclaimed = workItemStore.scan(WorkItemQuery.claimExpired(now));
        for (final WorkItem item : unclaimed) {
            if (item.lastReturnedToPoolAt != null) {
                item.accumulatedUnclaimedSeconds += Duration.between(item.lastReturnedToPoolAt, now).toSeconds();
            }
            item.lastReturnedToPoolAt = now;
            item.claimDeadline = computeNewClaimDeadline(item, now);
            workItemStore.put(item);

            final WorkItemLifecycleEvent claimExpiredEvent = WorkItemLifecycleEvent.of("CLAIM_EXPIRED", item, "system", null);
            claimEscalationPolicy.escalate(claimExpiredEvent);
            if (lifecycleEvent != null) {
                lifecycleEvent.fire(claimExpiredEvent);
            }
        }
    }

    /**
     * Computes the next claim deadline for a WorkItem that has returned to the pool.
     * Used by release and delegate transitions in {@link WorkItemService}.
     */
    public Instant computeNewClaimDeadline(final WorkItem item, final Instant now) {
        return claimSlaPolicy.computePoolDeadline(buildClaimSlaContext(item, now));
    }

    private ClaimSlaContext buildClaimSlaContext(final WorkItem item, final Instant now) {
        final Duration totalPoolSla = config.defaultClaimHours() > 0
                ? Duration.ofHours(config.defaultClaimHours())
                : Duration.ofHours(24);
        final Duration accumulated = Duration.ofSeconds(item.accumulatedUnclaimedSeconds);
        final Instant submitted = item.createdAt != null ? item.createdAt : now;
        return new ClaimSlaContext(submitted, totalPoolSla, accumulated, now);
    }
}
