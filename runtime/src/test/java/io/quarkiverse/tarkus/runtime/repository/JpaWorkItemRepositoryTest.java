package io.quarkiverse.tarkus.runtime.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.quarkiverse.tarkus.runtime.model.WorkItem;
import io.quarkiverse.tarkus.runtime.model.WorkItemPriority;
import io.quarkiverse.tarkus.runtime.model.WorkItemStatus;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Integration tests for {@link WorkItemRepository} JPA queries against real H2.
 *
 * <p>
 * Tests the repository-layer queries directly, bypassing the service layer so that
 * timestamps can be set to past values that would not be reachable via normal lifecycle
 * operations.
 *
 * <p>
 * {@link io.quarkiverse.tarkus.runtime.scheduler.ExpiryCleanupJob} depends on
 * {@link WorkItemRepository#findExpired} and
 * {@link WorkItemRepository#findUnclaimedPastDeadline} — correctness here is critical.
 */
@QuarkusTest
@TestTransaction
class JpaWorkItemRepositoryTest {

    @Inject
    WorkItemRepository workItemRepo;

    @Inject
    AuditEntryRepository auditRepo;

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    /**
     * Persists a WorkItem directly, bypassing the service, so that timestamps (especially
     * {@code expiresAt} and {@code claimDeadline}) can be set to arbitrary past or future
     * values required by the query tests.
     */
    private WorkItem persist(WorkItemStatus status, Instant expiresAt, Instant claimDeadline) {
        WorkItem wi = new WorkItem();
        wi.title = "Test";
        wi.status = status;
        wi.priority = WorkItemPriority.NORMAL;
        wi.createdAt = Instant.now();
        wi.updatedAt = Instant.now();
        wi.expiresAt = expiresAt;
        wi.claimDeadline = claimDeadline;
        return workItemRepo.save(wi);
    }

    // -------------------------------------------------------------------------
    // findExpired
    // -------------------------------------------------------------------------

    @Test
    void findExpired_returnsItemWithPastExpiryAndPendingStatus() {
        WorkItem target = persist(WorkItemStatus.PENDING, Instant.now().minusSeconds(3600), null);
        List<WorkItem> result = workItemRepo.findExpired(Instant.now());
        assertThat(result).extracting(wi -> wi.id).contains(target.id);
    }

    @Test
    void findExpired_returnsItemWithPastExpiryAndAssignedStatus() {
        WorkItem target = persist(WorkItemStatus.ASSIGNED, Instant.now().minusSeconds(3600), null);
        List<WorkItem> result = workItemRepo.findExpired(Instant.now());
        assertThat(result).extracting(wi -> wi.id).contains(target.id);
    }

    @Test
    void findExpired_returnsItemWithPastExpiryAndInProgressStatus() {
        WorkItem target = persist(WorkItemStatus.IN_PROGRESS, Instant.now().minusSeconds(3600), null);
        List<WorkItem> result = workItemRepo.findExpired(Instant.now());
        assertThat(result).extracting(wi -> wi.id).contains(target.id);
    }

    @Test
    void findExpired_returnsItemWithPastExpiryAndSuspendedStatus() {
        // CRITICAL: SUSPENDED must be included in findExpired — items cannot wait forever in suspension
        WorkItem target = persist(WorkItemStatus.SUSPENDED, Instant.now().minusSeconds(3600), null);
        List<WorkItem> result = workItemRepo.findExpired(Instant.now());
        assertThat(result).extracting(wi -> wi.id).contains(target.id);
    }

    @Test
    void findExpired_doesNotReturnCompletedItem() {
        WorkItem target = persist(WorkItemStatus.COMPLETED, Instant.now().minusSeconds(3600), null);
        List<WorkItem> result = workItemRepo.findExpired(Instant.now());
        assertThat(result).extracting(wi -> wi.id).doesNotContain(target.id);
    }

    @Test
    void findExpired_doesNotReturnCancelledItem() {
        WorkItem target = persist(WorkItemStatus.CANCELLED, Instant.now().minusSeconds(3600), null);
        List<WorkItem> result = workItemRepo.findExpired(Instant.now());
        assertThat(result).extracting(wi -> wi.id).doesNotContain(target.id);
    }

    @Test
    void findExpired_doesNotReturnFutureExpiry() {
        WorkItem target = persist(WorkItemStatus.PENDING, Instant.now().plusSeconds(3600), null);
        List<WorkItem> result = workItemRepo.findExpired(Instant.now());
        assertThat(result).extracting(wi -> wi.id).doesNotContain(target.id);
    }

    @Test
    void findExpired_nullExpiresAt_notReturned() {
        WorkItem target = persist(WorkItemStatus.PENDING, null, null);
        List<WorkItem> result = workItemRepo.findExpired(Instant.now());
        assertThat(result).extracting(wi -> wi.id).doesNotContain(target.id);
    }

    // -------------------------------------------------------------------------
    // findUnclaimedPastDeadline
    // -------------------------------------------------------------------------

    @Test
    void findUnclaimedPastDeadline_returnsPendingWithPastDeadline() {
        WorkItem target = persist(WorkItemStatus.PENDING, null, Instant.now().minusSeconds(3600));
        List<WorkItem> result = workItemRepo.findUnclaimedPastDeadline(Instant.now());
        assertThat(result).extracting(wi -> wi.id).contains(target.id);
    }

    @Test
    void findUnclaimedPastDeadline_doesNotReturnAssigned() {
        // Already claimed — claim deadline no longer relevant
        WorkItem target = persist(WorkItemStatus.ASSIGNED, null, Instant.now().minusSeconds(3600));
        List<WorkItem> result = workItemRepo.findUnclaimedPastDeadline(Instant.now());
        assertThat(result).extracting(wi -> wi.id).doesNotContain(target.id);
    }

    @Test
    void findUnclaimedPastDeadline_doesNotReturnFutureDeadline() {
        WorkItem target = persist(WorkItemStatus.PENDING, null, Instant.now().plusSeconds(3600));
        List<WorkItem> result = workItemRepo.findUnclaimedPastDeadline(Instant.now());
        assertThat(result).extracting(wi -> wi.id).doesNotContain(target.id);
    }

    @Test
    void findUnclaimedPastDeadline_nullClaimDeadline_notReturned() {
        WorkItem target = persist(WorkItemStatus.PENDING, null, null);
        List<WorkItem> result = workItemRepo.findUnclaimedPastDeadline(Instant.now());
        assertThat(result).extracting(wi -> wi.id).doesNotContain(target.id);
    }

    // -------------------------------------------------------------------------
    // findInbox JPA LIKE queries
    // -------------------------------------------------------------------------

    @Test
    void findInbox_JPA_byAssigneeId() {
        WorkItem target = persist(WorkItemStatus.ASSIGNED, null, null);
        target.assigneeId = "alice";
        workItemRepo.save(target);

        List<WorkItem> result = workItemRepo.findInbox("alice", null, null, null, null, null);
        assertThat(result).extracting(wi -> wi.id).contains(target.id);
    }

    @Test
    void findInbox_JPA_byCandidateGroupsLike() {
        WorkItem target = persist(WorkItemStatus.PENDING, null, null);
        target.candidateGroups = "team-a,team-b";
        workItemRepo.save(target);

        List<WorkItem> result = workItemRepo.findInbox(null, List.of("team-a"), null, null, null, null);
        assertThat(result).extracting(wi -> wi.id).contains(target.id);
    }

    @Test
    void findInbox_JPA_byMultipleCandidateGroups_OR() {
        WorkItem target = persist(WorkItemStatus.PENDING, null, null);
        target.candidateGroups = "team-c";
        workItemRepo.save(target);

        // "team-a" does not match, "team-c" does — OR logic must find it
        List<WorkItem> result = workItemRepo.findInbox(null, List.of("team-a", "team-c"), null, null, null, null);
        assertThat(result).extracting(wi -> wi.id).contains(target.id);
    }

    @Test
    void findInbox_JPA_byCandidateUsersLike() {
        WorkItem target = persist(WorkItemStatus.PENDING, null, null);
        target.candidateUsers = "bob,carol";
        workItemRepo.save(target);

        List<WorkItem> result = workItemRepo.findInbox("bob", null, null, null, null, null);
        assertThat(result).extracting(wi -> wi.id).contains(target.id);
    }

    @Test
    void findInbox_JPA_statusFilter() {
        WorkItem target = persist(WorkItemStatus.PENDING, null, null);
        target.assigneeId = "alice";
        workItemRepo.save(target);

        // Filtering for ASSIGNED should exclude the PENDING item
        List<WorkItem> result = workItemRepo.findInbox("alice", null, WorkItemStatus.ASSIGNED, null, null, null);
        assertThat(result).extracting(wi -> wi.id).doesNotContain(target.id);
    }

    @Test
    void findInbox_JPA_noActorFilter_emptyResult() {
        // When assignee=null and candidateGroups=empty, the (1=0) base means no actor matches
        // The caller (WorkItemResource) must handle this and not pass null/empty actor context
        persist(WorkItemStatus.PENDING, null, null);

        List<WorkItem> result = workItemRepo.findInbox(null, List.of(), null, null, null, null);
        assertThat(result).isEmpty();
    }
}
