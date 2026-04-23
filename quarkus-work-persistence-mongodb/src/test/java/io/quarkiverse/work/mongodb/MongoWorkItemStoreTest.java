package io.quarkiverse.work.mongodb;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkiverse.work.runtime.model.LabelPersistence;
import io.quarkiverse.work.runtime.model.WorkItem;
import io.quarkiverse.work.runtime.model.WorkItemLabel;
import io.quarkiverse.work.runtime.model.WorkItemPriority;
import io.quarkiverse.work.runtime.model.WorkItemStatus;
import io.quarkiverse.work.runtime.repository.WorkItemQuery;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class MongoWorkItemStoreTest {

    @Inject
    MongoWorkItemStore store;

    @BeforeEach
    void clearAll() {
        MongoWorkItemDocument.deleteAll();
    }

    // ── Put / Get ─────────────────────────────────────────────────────────────

    @Test
    void put_assignsId_whenAbsent() {
        final WorkItem wi = pending("alice", "Assign ID test");
        assertThat(wi.id).isNull();

        store.put(wi);

        assertThat(wi.id).isNotNull();
    }

    @Test
    void put_setsTimestamps() {
        final WorkItem wi = pending("alice", "Timestamps test");
        final Instant before = Instant.now().minusSeconds(1);

        store.put(wi);

        assertThat(wi.createdAt).isAfter(before);
        assertThat(wi.updatedAt).isAfterOrEqualTo(wi.createdAt);
    }

    @Test
    void put_and_get_roundtrip() {
        final WorkItem wi = pending("alice", "Roundtrip test");
        wi.description = "Do something important";
        wi.category = "review";
        wi.priority = WorkItemPriority.HIGH;
        wi.formKey = "review-form";
        wi.payload = "{\"ref\":\"PROJ-42\"}";

        store.put(wi);
        final Optional<WorkItem> found = store.get(wi.id);

        assertThat(found).isPresent();
        final WorkItem loaded = found.get();
        assertThat(loaded.title).isEqualTo("Roundtrip test");
        assertThat(loaded.description).isEqualTo("Do something important");
        assertThat(loaded.category).isEqualTo("review");
        assertThat(loaded.priority).isEqualTo(WorkItemPriority.HIGH);
        assertThat(loaded.formKey).isEqualTo("review-form");
        assertThat(loaded.payload).isEqualTo("{\"ref\":\"PROJ-42\"}");
        assertThat(loaded.status).isEqualTo(WorkItemStatus.PENDING);
        assertThat(loaded.createdBy).isEqualTo("alice");
    }

    @Test
    void get_returnsEmpty_whenNotFound() {
        assertThat(store.get(java.util.UUID.randomUUID())).isEmpty();
    }

    @Test
    void put_updatesExisting_onSecondCall() {
        final WorkItem wi = pending("alice", "Update test");
        store.put(wi);

        wi.status = WorkItemStatus.ASSIGNED;
        wi.assigneeId = "bob";
        store.put(wi);

        final WorkItem loaded = store.get(wi.id).orElseThrow();
        assertThat(loaded.status).isEqualTo(WorkItemStatus.ASSIGNED);
        assertThat(loaded.assigneeId).isEqualTo("bob");
        assertThat(MongoWorkItemDocument.count()).isEqualTo(1);
    }

    // ── ScanAll ───────────────────────────────────────────────────────────────

    @Test
    void scanAll_returnsAllDocuments() {
        store.put(pending("alice", "Item A"));
        store.put(pending("bob", "Item B"));
        store.put(pending("carol", "Item C"));

        assertThat(store.scanAll()).hasSize(3);
    }

    // ── Inbox (assignment OR) ─────────────────────────────────────────────────

    @Test
    void scan_inbox_byAssigneeId() {
        final WorkItem wi = pending("alice", "Direct assignee");
        wi.assigneeId = "alice";
        store.put(wi);
        store.put(pending("bob", "Other item"));

        final List<WorkItem> results = store.scan(WorkItemQuery.inbox("alice", null, null));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).title).isEqualTo("Direct assignee");
    }

    @Test
    void scan_inbox_byCandidateGroup() {
        final WorkItem wi = pending("system", "Group item");
        wi.candidateGroups = "finance-team,hr-team";
        store.put(wi);
        store.put(pending("system", "Other item"));

        final List<WorkItem> results = store.scan(
                WorkItemQuery.inbox(null, List.of("finance-team"), null));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).title).isEqualTo("Group item");
    }

    @Test
    void scan_inbox_byCandidateUsers() {
        final WorkItem wi = pending("system", "Candidate user item");
        wi.candidateUsers = "alice,bob";
        store.put(wi);
        store.put(pending("system", "Other"));

        final List<WorkItem> results = store.scan(WorkItemQuery.inbox(null, null, "alice"));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).title).isEqualTo("Candidate user item");
    }

    @Test
    void scan_inbox_orLogic_matchesAnyDimension() {
        final WorkItem wi = pending("system", "Multi-candidate item");
        wi.candidateGroups = "legal-team";
        wi.candidateUsers = "dave";
        store.put(wi);

        // alice is in legal-team → matches via group
        assertThat(store.scan(WorkItemQuery.inbox(null, List.of("legal-team"), null))).hasSize(1);
        // dave matches via candidateUsers
        assertThat(store.scan(WorkItemQuery.inbox(null, null, "dave"))).hasSize(1);
        // carol is not in any dimension → no match
        assertThat(store.scan(WorkItemQuery.inbox("carol", null, null))).hasSize(0);
    }

    // ── Status / Priority / Category filters ──────────────────────────────────

    @Test
    void scan_byStatus_exactMatch() {
        store.put(withStatus("alice", "Pending", WorkItemStatus.PENDING));
        store.put(withStatus("alice", "Assigned", WorkItemStatus.ASSIGNED));

        assertThat(store.scan(WorkItemQuery.builder().status(WorkItemStatus.PENDING).build())).hasSize(1);
        assertThat(store.scan(WorkItemQuery.builder().status(WorkItemStatus.ASSIGNED).build())).hasSize(1);
    }

    @Test
    void scan_byStatusIn() {
        store.put(withStatus("alice", "Pending", WorkItemStatus.PENDING));
        store.put(withStatus("alice", "InProgress", WorkItemStatus.IN_PROGRESS));
        store.put(withStatus("alice", "Completed", WorkItemStatus.COMPLETED));

        final List<WorkItem> results = store.scan(WorkItemQuery.builder()
                .statusIn(List.of(WorkItemStatus.PENDING, WorkItemStatus.IN_PROGRESS))
                .build());

        assertThat(results).hasSize(2)
                .extracting(w -> w.title)
                .containsExactlyInAnyOrder("Pending", "InProgress");
    }

    @Test
    void scan_byPriority() {
        final WorkItem hi = pending("alice", "High prio");
        hi.priority = WorkItemPriority.HIGH;
        store.put(hi);

        final WorkItem lo = pending("alice", "Low prio");
        lo.priority = WorkItemPriority.LOW;
        store.put(lo);

        assertThat(store.scan(WorkItemQuery.builder().priority(WorkItemPriority.HIGH).build()))
                .hasSize(1).first().extracting(w -> w.title).isEqualTo("High prio");
    }

    @Test
    void scan_byCategory() {
        final WorkItem wi = pending("alice", "Finance item");
        wi.category = "finance";
        store.put(wi);
        store.put(pending("alice", "No category"));

        assertThat(store.scan(WorkItemQuery.builder().category("finance").build())).hasSize(1);
        assertThat(store.scan(WorkItemQuery.builder().category("legal").build())).hasSize(0);
    }

    // ── Expired / ClaimExpired ────────────────────────────────────────────────

    @Test
    void scan_expired_returnsActiveItemsPastDeadline() {
        final Instant past = Instant.now().minus(1, ChronoUnit.HOURS);
        final Instant future = Instant.now().plus(1, ChronoUnit.HOURS);

        final WorkItem expired = withStatus("alice", "Expired", WorkItemStatus.PENDING);
        expired.expiresAt = past;
        store.put(expired);

        final WorkItem active = withStatus("alice", "Active", WorkItemStatus.PENDING);
        active.expiresAt = future;
        store.put(active);

        final WorkItem noDeadline = withStatus("alice", "No deadline", WorkItemStatus.PENDING);
        store.put(noDeadline);

        final List<WorkItem> results = store.scan(WorkItemQuery.expired(Instant.now()));
        assertThat(results).hasSize(1);
        assertThat(results.get(0).title).isEqualTo("Expired");
    }

    @Test
    void scan_expired_excludesTerminalStatuses() {
        final Instant past = Instant.now().minus(1, ChronoUnit.HOURS);

        final WorkItem completed = withStatus("alice", "Completed past deadline", WorkItemStatus.COMPLETED);
        completed.expiresAt = past;
        store.put(completed);

        assertThat(store.scan(WorkItemQuery.expired(Instant.now()))).hasSize(0);
    }

    @Test
    void scan_claimExpired_returnsPendingPastClaimDeadline() {
        final Instant past = Instant.now().minus(1, ChronoUnit.HOURS);

        final WorkItem claimExpired = withStatus("alice", "Claim expired", WorkItemStatus.PENDING);
        claimExpired.claimDeadline = past;
        store.put(claimExpired);

        final WorkItem assigned = withStatus("alice", "Assigned", WorkItemStatus.ASSIGNED);
        assigned.claimDeadline = past;
        store.put(assigned);

        final List<WorkItem> results = store.scan(WorkItemQuery.claimExpired(Instant.now()));
        assertThat(results).hasSize(1);
        assertThat(results.get(0).title).isEqualTo("Claim expired");
    }

    // ── Label pattern ─────────────────────────────────────────────────────────

    @Test
    void scan_byLabelPattern_exactMatch() {
        store.put(withLabel("alice", "Exact match", "legal/contracts"));
        store.put(withLabel("alice", "No match", "legal/ip"));
        store.put(pending("alice", "No labels"));

        assertThat(store.scan(WorkItemQuery.byLabelPattern("legal/contracts"))).hasSize(1);
    }

    @Test
    void scan_byLabelPattern_singleWildcard() {
        store.put(withLabel("alice", "Direct child", "legal/contracts"));
        store.put(withLabel("alice", "Deep child", "legal/contracts/nda"));
        store.put(withLabel("alice", "Other branch", "finance/budget"));

        final List<WorkItem> results = store.scan(WorkItemQuery.byLabelPattern("legal/*"));
        assertThat(results).hasSize(1);
        assertThat(results.get(0).title).isEqualTo("Direct child");
    }

    @Test
    void scan_byLabelPattern_doubleWildcard() {
        store.put(withLabel("alice", "Direct child", "legal/contracts"));
        store.put(withLabel("alice", "Deep child", "legal/contracts/nda"));
        store.put(withLabel("alice", "Other branch", "finance/budget"));

        final List<WorkItem> results = store.scan(WorkItemQuery.byLabelPattern("legal/**"));
        assertThat(results).hasSize(2);
    }

    // ── Label field roundtrip ─────────────────────────────────────────────────

    @Test
    void labels_roundtrip_preservesAllFields() {
        final WorkItem wi = pending("alice", "Label roundtrip");
        final WorkItemLabel label = new WorkItemLabel("legal/contracts", LabelPersistence.MANUAL, "alice");
        wi.labels = List.of(label);

        store.put(wi);
        final WorkItem loaded = store.get(wi.id).orElseThrow();

        assertThat(loaded.labels).hasSize(1);
        assertThat(loaded.labels.get(0).path).isEqualTo("legal/contracts");
        assertThat(loaded.labels.get(0).persistence).isEqualTo(LabelPersistence.MANUAL);
        assertThat(loaded.labels.get(0).appliedBy).isEqualTo("alice");
    }

    @Test
    void candidateGroups_roundtrip_commaSeparatedPreserved() {
        final WorkItem wi = pending("system", "Group routing");
        wi.candidateGroups = "finance-team,hr-team";

        store.put(wi);
        final WorkItem loaded = store.get(wi.id).orElseThrow();

        assertThat(loaded.candidateGroups).isEqualTo("finance-team,hr-team");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private WorkItem pending(final String createdBy, final String title) {
        final WorkItem wi = new WorkItem();
        wi.title = title;
        wi.createdBy = createdBy;
        wi.status = WorkItemStatus.PENDING;
        return wi;
    }

    private WorkItem withStatus(final String createdBy, final String title, final WorkItemStatus status) {
        final WorkItem wi = pending(createdBy, title);
        wi.status = status;
        return wi;
    }

    private WorkItem withLabel(final String createdBy, final String title, final String labelPath) {
        final WorkItem wi = pending(createdBy, title);
        wi.labels = new java.util.ArrayList<>();
        wi.labels.add(new WorkItemLabel(labelPath, LabelPersistence.MANUAL, createdBy));
        return wi;
    }
}
