package io.casehub.work.testing;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.work.runtime.model.AuditEntry;
import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.runtime.model.WorkItemPriority;
import io.casehub.work.runtime.model.WorkItemStatus;
import io.casehub.work.runtime.repository.WorkItemQuery;

/**
 * Pure JUnit 5 tests for the in-memory store implementations.
 *
 * <p>
 * No {@code @QuarkusTest} — the implementations are plain Java objects and can be
 * constructed and exercised without a CDI container.
 */
class InMemoryRepositoryTest {

    private InMemoryWorkItemStore workItemStore;
    private InMemoryAuditEntryStore auditStore;

    @BeforeEach
    void setUp() {
        workItemStore = new InMemoryWorkItemStore();
        auditStore = new InMemoryAuditEntryStore();
    }

    // =========================================================================
    // WorkItemStore — basic CRUD
    // =========================================================================

    @Test
    void save_assignsUuidIfAbsent() {
        final WorkItem wi = workItem(WorkItemStatus.PENDING);
        assertThat(wi.id).isNull();

        workItemStore.put(wi);

        assertThat(wi.id).isNotNull();
    }

    @Test
    void save_returnsPersistedItem() {
        final WorkItem wi = workItem(WorkItemStatus.PENDING);
        workItemStore.put(wi);

        assertThat(workItemStore.get(wi.id)).isPresent().hasValue(wi);
    }

    @Test
    void findById_absent_returnsEmpty() {
        assertThat(workItemStore.get(UUID.randomUUID())).isEmpty();
    }

    @Test
    void findAll_returnsAllSaved() {
        workItemStore.put(workItem(WorkItemStatus.PENDING));
        workItemStore.put(workItem(WorkItemStatus.ASSIGNED));
        workItemStore.put(workItem(WorkItemStatus.IN_PROGRESS));

        assertThat(workItemStore.scan(WorkItemQuery.all())).hasSize(3);
    }

    @Test
    void clear_removesAll() {
        workItemStore.put(workItem(WorkItemStatus.PENDING));
        workItemStore.put(workItem(WorkItemStatus.ASSIGNED));

        workItemStore.clear();

        assertThat(workItemStore.scan(WorkItemQuery.all())).isEmpty();
    }

    // =========================================================================
    // WorkItemStore — inbox assignment filters
    // =========================================================================

    @Test
    void findInbox_byAssignee() {
        final WorkItem wi = workItem(WorkItemStatus.ASSIGNED);
        wi.assigneeId = "alice";
        workItemStore.put(wi);

        final List<WorkItem> result = workItemStore.scan(WorkItemQuery.inbox("alice", null, null));

        assertThat(result).containsExactly(wi);
    }

    @Test
    void findInbox_byCandidateGroup() {
        final WorkItem wi = workItem(WorkItemStatus.PENDING);
        wi.candidateGroups = "team-a,team-b";
        workItemStore.put(wi);

        final List<WorkItem> result = workItemStore.scan(WorkItemQuery.inbox(null, List.of("team-a"), null));

        assertThat(result).containsExactly(wi);
    }

    @Test
    void findInbox_byCandidateUser_exactMatch() {
        final WorkItem wi = workItem(WorkItemStatus.PENDING);
        wi.candidateUsers = "bob";
        workItemStore.put(wi);

        final List<WorkItem> result = workItemStore.scan(WorkItemQuery.inbox("bob", null, null));

        assertThat(result).containsExactly(wi);
    }

    @Test
    void findInbox_candidateUser_noPartialMatch() {
        final WorkItem wi = workItem(WorkItemStatus.PENDING);
        wi.candidateUsers = "bobby";
        workItemStore.put(wi);

        // "bob" must NOT match "bobby" — token matching, not substring
        final List<WorkItem> result = workItemStore.scan(WorkItemQuery.inbox("bob", null, null));

        assertThat(result).isEmpty();
    }

    // =========================================================================
    // WorkItemStore — inbox additional filters
    // =========================================================================

    @Test
    void findInbox_statusFilter() {
        final WorkItem wi = workItem(WorkItemStatus.COMPLETED);
        wi.assigneeId = "alice";
        workItemStore.put(wi);

        final List<WorkItem> result = workItemStore.scan(
                WorkItemQuery.inbox("alice", null, null).toBuilder().status(WorkItemStatus.PENDING).build());

        assertThat(result).isEmpty();
    }

    @Test
    void findInbox_categoryFilter() {
        final WorkItem wi = workItem(WorkItemStatus.PENDING);
        wi.assigneeId = "alice";
        wi.category = "finance";
        workItemStore.put(wi);

        final List<WorkItem> result = workItemStore.scan(
                WorkItemQuery.inbox("alice", null, null).toBuilder().category("legal").build());

        assertThat(result).isEmpty();
    }

    // =========================================================================
    // WorkItemStore — expiry and deadline queries
    // =========================================================================

    @Test
    void findExpired() {
        final Instant fiveMinutesAgo = Instant.now().minus(5, ChronoUnit.MINUTES);

        final WorkItem expired = workItem(WorkItemStatus.PENDING);
        expired.expiresAt = fiveMinutesAgo;
        workItemStore.put(expired);

        final WorkItem alreadyCompleted = workItem(WorkItemStatus.COMPLETED);
        alreadyCompleted.expiresAt = fiveMinutesAgo;
        workItemStore.put(alreadyCompleted);

        final WorkItem notExpired = workItem(WorkItemStatus.PENDING);
        notExpired.expiresAt = Instant.now().plus(1, ChronoUnit.HOURS);
        workItemStore.put(notExpired);

        final List<WorkItem> result = workItemStore.scan(WorkItemQuery.expired(Instant.now()));

        assertThat(result).containsExactly(expired);
    }

    @Test
    void findUnclaimedPastDeadline() {
        final Instant fiveMinutesAgo = Instant.now().minus(5, ChronoUnit.MINUTES);

        final WorkItem unclaimed = workItem(WorkItemStatus.PENDING);
        unclaimed.claimDeadline = fiveMinutesAgo;
        workItemStore.put(unclaimed);

        final WorkItem assigned = workItem(WorkItemStatus.ASSIGNED);
        assigned.claimDeadline = fiveMinutesAgo;
        workItemStore.put(assigned);

        final WorkItem futureDeadline = workItem(WorkItemStatus.PENDING);
        futureDeadline.claimDeadline = Instant.now().plus(1, ChronoUnit.HOURS);
        workItemStore.put(futureDeadline);

        final List<WorkItem> result = workItemStore.scan(WorkItemQuery.claimExpired(Instant.now()));

        assertThat(result).containsExactly(unclaimed);
    }

    // =========================================================================
    // AuditEntryStore
    // =========================================================================

    @Test
    void append_assignsUuidAndOccurredAt() {
        final AuditEntry entry = auditEntry(UUID.randomUUID(), "CREATED");
        entry.id = null;
        entry.occurredAt = null;

        auditStore.append(entry);

        assertThat(entry.id).isNotNull();
        assertThat(entry.occurredAt).isNotNull();
    }

    @Test
    void findByWorkItemId_returnsOnlyMatchingEntries() {
        final UUID workItemId1 = UUID.randomUUID();
        final UUID workItemId2 = UUID.randomUUID();

        auditStore.append(auditEntry(workItemId1, "CREATED"));
        auditStore.append(auditEntry(workItemId1, "ASSIGNED"));
        auditStore.append(auditEntry(workItemId2, "CREATED"));

        final List<AuditEntry> result = auditStore.findByWorkItemId(workItemId1);

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(e -> workItemId1.equals(e.workItemId));
    }

    @Test
    void findByWorkItemId_orderedByOccurredAt() {
        final UUID workItemId = UUID.randomUUID();

        final Instant t1 = Instant.now().minus(10, ChronoUnit.MINUTES);
        final Instant t2 = Instant.now().minus(5, ChronoUnit.MINUTES);
        final Instant t3 = Instant.now();

        // Append in reverse chronological order
        final AuditEntry third = auditEntry(workItemId, "COMPLETED");
        third.occurredAt = t3;
        auditStore.append(third);

        final AuditEntry second = auditEntry(workItemId, "ASSIGNED");
        second.occurredAt = t2;
        auditStore.append(second);

        final AuditEntry first = auditEntry(workItemId, "CREATED");
        first.occurredAt = t1;
        auditStore.append(first);

        final List<AuditEntry> result = auditStore.findByWorkItemId(workItemId);

        assertThat(result).extracting(e -> e.event)
                .containsExactly("CREATED", "ASSIGNED", "COMPLETED");
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private WorkItem workItem(final WorkItemStatus status) {
        final WorkItem wi = new WorkItem();
        wi.status = status;
        wi.priority = WorkItemPriority.MEDIUM;
        wi.title = "Test";
        wi.createdAt = Instant.now();
        wi.updatedAt = Instant.now();
        return wi;
    }

    private AuditEntry auditEntry(final UUID workItemId, final String event) {
        final AuditEntry entry = new AuditEntry();
        entry.workItemId = workItemId;
        entry.event = event;
        entry.occurredAt = Instant.now();
        return entry;
    }
}
