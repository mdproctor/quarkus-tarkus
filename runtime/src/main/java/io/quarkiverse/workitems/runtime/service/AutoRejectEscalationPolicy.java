package io.quarkiverse.tarkus.runtime.service;

import java.time.Instant;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkiverse.tarkus.runtime.model.AuditEntry;
import io.quarkiverse.tarkus.runtime.model.WorkItem;
import io.quarkiverse.tarkus.runtime.model.WorkItemStatus;
import io.quarkiverse.tarkus.runtime.repository.AuditEntryRepository;
import io.quarkiverse.tarkus.runtime.repository.WorkItemRepository;

@ApplicationScoped
public class AutoRejectEscalationPolicy implements EscalationPolicy {

    @Inject
    AuditEntryRepository auditRepo;

    @Inject
    WorkItemRepository workItemRepo;

    @Override
    public void onExpired(WorkItem workItem) {
        workItem.status = WorkItemStatus.REJECTED;
        workItem.completedAt = Instant.now();
        workItemRepo.save(workItem);

        final AuditEntry entry = new AuditEntry();
        entry.workItemId = workItem.id;
        entry.event = "REJECTED";
        entry.actor = "system";
        entry.detail = "auto-rejected: expiry deadline exceeded";
        entry.occurredAt = Instant.now();
        auditRepo.append(entry);
    }

    @Override
    public void onUnclaimedPastDeadline(WorkItem workItem) {
        // Auto-reject only fires on expiry, not claim deadline breach
        // Claim deadline breach uses the claim escalation policy separately
    }
}
