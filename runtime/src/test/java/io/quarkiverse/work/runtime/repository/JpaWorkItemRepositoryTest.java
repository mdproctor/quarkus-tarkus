package io.casehub.work.runtime.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.casehub.work.runtime.model.LabelPersistence;
import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.runtime.model.WorkItemLabel;
import io.casehub.work.runtime.model.WorkItemPriority;
import io.casehub.work.runtime.model.WorkItemStatus;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Integration tests for {@link WorkItemStore} JPA queries against real H2.
 *
 * <p>
 * Tests the store-layer queries directly, bypassing the service layer so that
 * timestamps can be set to past values that would not be reachable via normal lifecycle
 * operations.
 *
 * <p>
 * {@link io.casehub.work.runtime.service.ExpiryCleanupJob} depends on
 * {@link WorkItemStore#scan} with {@link WorkItemQuery#expired} and
 * {@link WorkItemQuery#claimExpired} — correctness here is critical.
 */
@QuarkusTest
@TestTransaction
class JpaWorkItemRepositoryTest {

    @Inject
    WorkItemStore workItemStore;

    @Inject
    AuditEntryStore auditStore;

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
        return workItemStore.put(wi);
    }

    // -------------------------------------------------------------------------
    // findExpired
    // -------------------------------------------------------------------------

    @Test
    void findExpired_returnsItemWithPastExpiryAndPendingStatus() {
        WorkItem target = persist(WorkItemStatus.PENDING, Instant.now().minusSeconds(3600), null);
        List<WorkItem> result = workItemStore.scan(WorkItemQuery.expired(Instant.now()));
        assertThat(result).extracting(wi -> wi.id).contains(target.id);
    }

    @Test
    void findExpired_returnsItemWithPastExpiryAndAssignedStatus() {
        WorkItem target = persist(WorkItemStatus.ASSIGNED, Instant.now().minusSeconds(3600), null);
        List<WorkItem> result = workItemStore.scan(WorkItemQuery.expired(Instant.now()));
        assertThat(result).extracting(wi -> wi.id).contains(target.id);
    }

    @Test
    void findExpired_returnsItemWithPastExpiryAndInProgressStatus() {
        WorkItem target = persist(WorkItemStatus.IN_PROGRESS, Instant.now().minusSeconds(3600), null);
        List<WorkItem> result = workItemStore.scan(WorkItemQuery.expired(Instant.now()));
        assertThat(result).extracting(wi -> wi.id).contains(target.id);
    }

    @Test
    void findExpired_returnsItemWithPastExpiryAndSuspendedStatus() {
        // CRITICAL: SUSPENDED must be included in findExpired — items cannot wait forever in suspension
        WorkItem target = persist(WorkItemStatus.SUSPENDED, Instant.now().minusSeconds(3600), null);
        List<WorkItem> result = workItemStore.scan(WorkItemQuery.expired(Instant.now()));
        assertThat(result).extracting(wi -> wi.id).contains(target.id);
    }

    @Test
    void findExpired_doesNotReturnCompletedItem() {
        WorkItem target = persist(WorkItemStatus.COMPLETED, Instant.now().minusSeconds(3600), null);
        List<WorkItem> result = workItemStore.scan(WorkItemQuery.expired(Instant.now()));
        assertThat(result).extracting(wi -> wi.id).doesNotContain(target.id);
    }

    @Test
    void findExpired_doesNotReturnCancelledItem() {
        WorkItem target = persist(WorkItemStatus.CANCELLED, Instant.now().minusSeconds(3600), null);
        List<WorkItem> result = workItemStore.scan(WorkItemQuery.expired(Instant.now()));
        assertThat(result).extracting(wi -> wi.id).doesNotContain(target.id);
    }

    @Test
    void findExpired_doesNotReturnFutureExpiry() {
        WorkItem target = persist(WorkItemStatus.PENDING, Instant.now().plusSeconds(3600), null);
        List<WorkItem> result = workItemStore.scan(WorkItemQuery.expired(Instant.now()));
        assertThat(result).extracting(wi -> wi.id).doesNotContain(target.id);
    }

    @Test
    void findExpired_nullExpiresAt_notReturned() {
        WorkItem target = persist(WorkItemStatus.PENDING, null, null);
        List<WorkItem> result = workItemStore.scan(WorkItemQuery.expired(Instant.now()));
        assertThat(result).extracting(wi -> wi.id).doesNotContain(target.id);
    }

    // -------------------------------------------------------------------------
    // findUnclaimedPastDeadline
    // -------------------------------------------------------------------------

    @Test
    void findUnclaimedPastDeadline_returnsPendingWithPastDeadline() {
        WorkItem target = persist(WorkItemStatus.PENDING, null, Instant.now().minusSeconds(3600));
        List<WorkItem> result = workItemStore.scan(WorkItemQuery.claimExpired(Instant.now()));
        assertThat(result).extracting(wi -> wi.id).contains(target.id);
    }

    @Test
    void findUnclaimedPastDeadline_doesNotReturnAssigned() {
        // Already claimed — claim deadline no longer relevant
        WorkItem target = persist(WorkItemStatus.ASSIGNED, null, Instant.now().minusSeconds(3600));
        List<WorkItem> result = workItemStore.scan(WorkItemQuery.claimExpired(Instant.now()));
        assertThat(result).extracting(wi -> wi.id).doesNotContain(target.id);
    }

    @Test
    void findUnclaimedPastDeadline_doesNotReturnFutureDeadline() {
        WorkItem target = persist(WorkItemStatus.PENDING, null, Instant.now().plusSeconds(3600));
        List<WorkItem> result = workItemStore.scan(WorkItemQuery.claimExpired(Instant.now()));
        assertThat(result).extracting(wi -> wi.id).doesNotContain(target.id);
    }

    @Test
    void findUnclaimedPastDeadline_nullClaimDeadline_notReturned() {
        WorkItem target = persist(WorkItemStatus.PENDING, null, null);
        List<WorkItem> result = workItemStore.scan(WorkItemQuery.claimExpired(Instant.now()));
        assertThat(result).extracting(wi -> wi.id).doesNotContain(target.id);
    }

    // -------------------------------------------------------------------------
    // findInbox JPA LIKE queries
    // -------------------------------------------------------------------------

    @Test
    void findInbox_JPA_byAssigneeId() {
        WorkItem target = persist(WorkItemStatus.ASSIGNED, null, null);
        target.assigneeId = "alice";
        workItemStore.put(target);

        List<WorkItem> result = workItemStore.scan(WorkItemQuery.inbox("alice", null, null));
        assertThat(result).extracting(wi -> wi.id).contains(target.id);
    }

    @Test
    void findInbox_JPA_byCandidateGroupsLike() {
        WorkItem target = persist(WorkItemStatus.PENDING, null, null);
        target.candidateGroups = "team-a,team-b";
        workItemStore.put(target);

        List<WorkItem> result = workItemStore.scan(WorkItemQuery.inbox(null, List.of("team-a"), null));
        assertThat(result).extracting(wi -> wi.id).contains(target.id);
    }

    @Test
    void findInbox_JPA_byMultipleCandidateGroups_OR() {
        WorkItem target = persist(WorkItemStatus.PENDING, null, null);
        target.candidateGroups = "team-c";
        workItemStore.put(target);

        // "team-a" does not match, "team-c" does — OR logic must find it
        List<WorkItem> result = workItemStore.scan(WorkItemQuery.inbox(null, List.of("team-a", "team-c"), null));
        assertThat(result).extracting(wi -> wi.id).contains(target.id);
    }

    @Test
    void findInbox_JPA_byCandidateUsersLike() {
        WorkItem target = persist(WorkItemStatus.PENDING, null, null);
        target.candidateUsers = "bob,carol";
        workItemStore.put(target);

        List<WorkItem> result = workItemStore.scan(WorkItemQuery.inbox("bob", null, null));
        assertThat(result).extracting(wi -> wi.id).contains(target.id);
    }

    @Test
    void findInbox_JPA_statusFilter() {
        WorkItem target = persist(WorkItemStatus.PENDING, null, null);
        target.assigneeId = "alice";
        workItemStore.put(target);

        // Filtering for ASSIGNED should exclude the PENDING item
        List<WorkItem> result = workItemStore.scan(
                WorkItemQuery.inbox("alice", null, null).toBuilder().status(WorkItemStatus.ASSIGNED).build());
        assertThat(result).extracting(wi -> wi.id).doesNotContain(target.id);
    }

    @Test
    void findInbox_JPA_noActorFilter_returnsAll() {
        // With the new KV-native scan() semantics, no assignment criteria = no assignment constraint
        // inbox(null, [], null) is equivalent to all() — no actor filter applied
        WorkItem target = persist(WorkItemStatus.PENDING, null, null);

        List<WorkItem> result = workItemStore.scan(WorkItemQuery.inbox(null, List.of(), null));
        assertThat(result).extracting(wi -> wi.id).contains(target.id);
    }

    // -------------------------------------------------------------------------
    // findByLabelPattern
    // -------------------------------------------------------------------------

    @Test
    @jakarta.transaction.Transactional
    void findByLabelPattern_exactMatch_returnMatchingItems() {
        var wi = new WorkItem();
        wi.title = "label-test-exact";
        wi.status = WorkItemStatus.PENDING;
        wi.priority = WorkItemPriority.NORMAL;
        wi.labels.add(new WorkItemLabel("legal/contracts", LabelPersistence.MANUAL, "alice"));
        workItemStore.put(wi);

        var results = workItemStore.scan(WorkItemQuery.byLabelPattern("legal/contracts"));

        assertThat(results).extracting(w -> w.title).contains("label-test-exact");
    }

    @Test
    @jakarta.transaction.Transactional
    void findByLabelPattern_singleWildcard_matchesOneLevel() {
        var wi = new WorkItem();
        wi.title = "label-test-wildcard";
        wi.status = WorkItemStatus.PENDING;
        wi.priority = WorkItemPriority.NORMAL;
        wi.labels.add(new WorkItemLabel("legal/contracts", LabelPersistence.MANUAL, "alice"));
        workItemStore.put(wi);

        assertThat(workItemStore.scan(WorkItemQuery.byLabelPattern("legal/*")))
                .extracting(w -> w.title).contains("label-test-wildcard");

        assertThat(workItemStore.scan(WorkItemQuery.byLabelPattern("legal/contracts/*")))
                .extracting(w -> w.title).doesNotContain("label-test-wildcard");
    }

    @Test
    @jakarta.transaction.Transactional
    void findByLabelPattern_multiWildcard_matchesAllDepths() {
        var wi1 = new WorkItem();
        wi1.title = "label-test-multi-1";
        wi1.status = WorkItemStatus.PENDING;
        wi1.priority = WorkItemPriority.NORMAL;
        wi1.labels.add(new WorkItemLabel("legal/contracts", LabelPersistence.MANUAL, "alice"));
        workItemStore.put(wi1);

        var wi2 = new WorkItem();
        wi2.title = "label-test-multi-2";
        wi2.status = WorkItemStatus.PENDING;
        wi2.priority = WorkItemPriority.NORMAL;
        wi2.labels.add(new WorkItemLabel("legal/contracts/nda", LabelPersistence.MANUAL, "alice"));
        workItemStore.put(wi2);

        assertThat(workItemStore.scan(WorkItemQuery.byLabelPattern("legal/**")))
                .extracting(w -> w.title)
                .contains("label-test-multi-1", "label-test-multi-2");
    }

    @Test
    @jakarta.transaction.Transactional
    void findByLabelPattern_noMatch_returnsEmpty() {
        assertThat(workItemStore.scan(WorkItemQuery.byLabelPattern("nonexistent/path"))).isEmpty();
    }
}
