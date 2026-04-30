package io.casehub.work.runtime.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.casehub.work.runtime.model.AuditEntry;
import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.runtime.model.WorkItemPriority;
import io.casehub.work.runtime.model.WorkItemStatus;
import io.casehub.work.runtime.repository.AuditEntryStore;
import io.casehub.work.runtime.repository.WorkItemStore;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Integration tests for the lifecycle engine jobs.
 *
 * <p>
 * The Quarkus scheduler is disabled in test application.properties so that jobs do not
 * fire automatically. Tests invoke {@link ExpiryCleanupJob#checkExpired()} and
 * {@link ClaimDeadlineJob#checkUnclaimedPastDeadline()} directly.
 *
 * <p>
 * These are RED-phase tests: they will not compile until
 * {@code ExpiryCleanupJob} and {@code ClaimDeadlineJob} production classes exist.
 */
@QuarkusTest
@TestTransaction
class LifecycleEngineTest {

    @Inject
    ExpiryCleanupJob expiryJob;

    @Inject
    ClaimDeadlineJob claimDeadlineJob;

    @Inject
    WorkItemStore workItemStore;

    @Inject
    AuditEntryStore auditStore;

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Persists a WorkItem directly with {@code expiresAt} set 2 hours in the past,
     * bypassing the service layer so any active status can be combined with a
     * past expiry timestamp.
     */
    private WorkItem createExpiredItem(WorkItemStatus status) {
        WorkItem wi = new WorkItem();
        wi.title = "Expiry test";
        wi.status = status;
        wi.priority = WorkItemPriority.NORMAL;
        wi.createdAt = Instant.now();
        wi.updatedAt = Instant.now();
        wi.expiresAt = Instant.now().minus(2, ChronoUnit.HOURS);
        return workItemStore.put(wi);
    }

    /**
     * Persists a PENDING WorkItem with {@code claimDeadline} set 1 hour in the past.
     */
    private WorkItem createPastClaimDeadlineItem() {
        WorkItem wi = new WorkItem();
        wi.title = "Claim deadline test";
        wi.status = WorkItemStatus.PENDING;
        wi.priority = WorkItemPriority.NORMAL;
        wi.createdAt = Instant.now();
        wi.updatedAt = Instant.now();
        wi.claimDeadline = Instant.now().minus(1, ChronoUnit.HOURS);
        return workItemStore.put(wi);
    }

    // -------------------------------------------------------------------------
    // ExpiryCleanupJob — active statuses all transition to EXPIRED
    // -------------------------------------------------------------------------

    @Test
    void expiry_pendingItemPastDeadline_transitionsToExpired() {
        WorkItem wi = createExpiredItem(WorkItemStatus.PENDING);
        expiryJob.checkExpired();
        WorkItem reloaded = workItemStore.get(wi.id).orElseThrow();
        assertThat(reloaded.status).isEqualTo(WorkItemStatus.EXPIRED);
    }

    @Test
    void expiry_assignedItemPastDeadline_transitionsToExpired() {
        WorkItem wi = createExpiredItem(WorkItemStatus.ASSIGNED);
        expiryJob.checkExpired();
        WorkItem reloaded = workItemStore.get(wi.id).orElseThrow();
        assertThat(reloaded.status).isEqualTo(WorkItemStatus.EXPIRED);
    }

    @Test
    void expiry_inProgressItemPastDeadline_transitionsToExpired() {
        WorkItem wi = createExpiredItem(WorkItemStatus.IN_PROGRESS);
        expiryJob.checkExpired();
        WorkItem reloaded = workItemStore.get(wi.id).orElseThrow();
        assertThat(reloaded.status).isEqualTo(WorkItemStatus.EXPIRED);
    }

    @Test
    void expiry_suspendedItemPastDeadline_transitionsToExpired() {
        WorkItem wi = createExpiredItem(WorkItemStatus.SUSPENDED);
        expiryJob.checkExpired();
        WorkItem reloaded = workItemStore.get(wi.id).orElseThrow();
        assertThat(reloaded.status).isEqualTo(WorkItemStatus.EXPIRED);
    }

    @Test
    void expiry_completedItemNotAffected() {
        WorkItem completed = new WorkItem();
        completed.title = "Completed";
        completed.status = WorkItemStatus.COMPLETED;
        completed.priority = WorkItemPriority.NORMAL;
        completed.createdAt = Instant.now();
        completed.updatedAt = Instant.now();
        completed.expiresAt = Instant.now().minus(1, ChronoUnit.HOURS);
        completed.completedAt = Instant.now().minus(30, ChronoUnit.MINUTES);
        workItemStore.put(completed);

        expiryJob.checkExpired();

        WorkItem reloaded = workItemStore.get(completed.id).orElseThrow();
        assertThat(reloaded.status).isEqualTo(WorkItemStatus.COMPLETED);
    }

    @Test
    void expiry_futureDeadlineNotTriggered() {
        WorkItem wi = new WorkItem();
        wi.title = "Future expiry";
        wi.status = WorkItemStatus.PENDING;
        wi.priority = WorkItemPriority.NORMAL;
        wi.createdAt = Instant.now();
        wi.updatedAt = Instant.now();
        wi.expiresAt = Instant.now().plus(2, ChronoUnit.HOURS);
        workItemStore.put(wi);

        expiryJob.checkExpired();

        WorkItem reloaded = workItemStore.get(wi.id).orElseThrow();
        assertThat(reloaded.status).isEqualTo(WorkItemStatus.PENDING);
    }

    @Test
    void expiry_writesExpiredAuditEntry() {
        WorkItem wi = createExpiredItem(WorkItemStatus.PENDING);
        expiryJob.checkExpired();
        List<AuditEntry> trail = auditStore.findByWorkItemId(wi.id);
        assertThat(trail).anyMatch(e -> "EXPIRED".equals(e.event) && "system".equals(e.actor));
    }

    @Test
    void expiry_multipleItemsAllProcessed() {
        WorkItem wi1 = createExpiredItem(WorkItemStatus.PENDING);
        WorkItem wi2 = createExpiredItem(WorkItemStatus.ASSIGNED);
        WorkItem wi3 = createExpiredItem(WorkItemStatus.IN_PROGRESS);

        expiryJob.checkExpired();

        assertThat(workItemStore.get(wi1.id).orElseThrow().status).isEqualTo(WorkItemStatus.EXPIRED);
        assertThat(workItemStore.get(wi2.id).orElseThrow().status).isEqualTo(WorkItemStatus.EXPIRED);
        assertThat(workItemStore.get(wi3.id).orElseThrow().status).isEqualTo(WorkItemStatus.EXPIRED);
    }

    // -------------------------------------------------------------------------
    // ClaimDeadlineJob
    // -------------------------------------------------------------------------

    @Test
    void claimDeadline_pendingItemPastDeadline_jobRuns() {
        WorkItem wi = createPastClaimDeadlineItem();
        // Notify policy is the default — no status change expected, but job must not throw
        assertThatCode(() -> claimDeadlineJob.checkUnclaimedPastDeadline()).doesNotThrowAnyException();
        WorkItem reloaded = workItemStore.get(wi.id).orElseThrow();
        assertThat(reloaded.status).isEqualTo(WorkItemStatus.PENDING);
    }

    @Test
    void claimDeadline_assignedItemNotAffected() {
        WorkItem wi = new WorkItem();
        wi.title = "Assigned past claim deadline";
        wi.status = WorkItemStatus.ASSIGNED;
        wi.priority = WorkItemPriority.NORMAL;
        wi.createdAt = Instant.now();
        wi.updatedAt = Instant.now();
        wi.claimDeadline = Instant.now().minus(1, ChronoUnit.HOURS);
        wi.assigneeId = "alice";
        workItemStore.put(wi);

        claimDeadlineJob.checkUnclaimedPastDeadline();

        WorkItem reloaded = workItemStore.get(wi.id).orElseThrow();
        assertThat(reloaded.status).isEqualTo(WorkItemStatus.ASSIGNED);
    }

    // -------------------------------------------------------------------------
    // Mixed past/future — only past expiry processed
    // -------------------------------------------------------------------------

    @Test
    void expiry_onlyPastExpiryProcessed() {
        WorkItem past = createExpiredItem(WorkItemStatus.PENDING);

        WorkItem future = new WorkItem();
        future.title = "Future expiry";
        future.status = WorkItemStatus.PENDING;
        future.priority = WorkItemPriority.NORMAL;
        future.createdAt = Instant.now();
        future.updatedAt = Instant.now();
        future.expiresAt = Instant.now().plus(2, ChronoUnit.HOURS);
        workItemStore.put(future);

        expiryJob.checkExpired();

        assertThat(workItemStore.get(past.id).orElseThrow().status).isEqualTo(WorkItemStatus.EXPIRED);
        assertThat(workItemStore.get(future.id).orElseThrow().status).isEqualTo(WorkItemStatus.PENDING);
    }
}
