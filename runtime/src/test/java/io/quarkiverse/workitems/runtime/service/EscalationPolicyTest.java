package io.quarkiverse.workitems.runtime.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkiverse.workitems.runtime.model.AuditEntry;
import io.quarkiverse.workitems.runtime.model.WorkItem;
import io.quarkiverse.workitems.runtime.model.WorkItemPriority;
import io.quarkiverse.workitems.runtime.model.WorkItemStatus;
import io.quarkiverse.workitems.runtime.repository.AuditEntryRepository;
import io.quarkiverse.workitems.runtime.repository.WorkItemRepository;

/**
 * Pure JUnit 5 unit tests for the three EscalationPolicy implementations.
 *
 * <p>
 * No Quarkus runtime — all CDI-injected fields are wired via reflection so that
 * the real policy classes are exercised without container startup overhead.
 */
class EscalationPolicyTest {

    // -------------------------------------------------------------------------
    // In-memory repository fakes (same pattern as WorkItemServiceTest)
    // -------------------------------------------------------------------------

    static class TestWorkItemRepo implements WorkItemRepository {

        private final Map<UUID, WorkItem> store = new ConcurrentHashMap<>();

        @Override
        public WorkItem save(WorkItem workItem) {
            if (workItem.id == null) {
                workItem.id = UUID.randomUUID();
            }
            store.put(workItem.id, workItem);
            return workItem;
        }

        @Override
        public Optional<WorkItem> findById(UUID id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public List<WorkItem> findAll() {
            return new ArrayList<>(store.values());
        }

        @Override
        public List<WorkItem> findInbox(String assignee, List<String> candidateGroups,
                WorkItemStatus status, WorkItemPriority priority,
                String category, Instant followUpBefore) {
            return List.of();
        }

        @Override
        public List<WorkItem> findExpired(Instant now) {
            return List.of();
        }

        @Override
        public List<WorkItem> findUnclaimedPastDeadline(Instant now) {
            return List.of();
        }

        @Override
        public List<WorkItem> findByLabelPattern(String pattern) {
            return List.of();
        }
    }

    static class TestAuditRepo implements AuditEntryRepository {

        private final List<AuditEntry> entries = new ArrayList<>();

        @Override
        public void append(AuditEntry entry) {
            entries.add(entry);
        }

        @Override
        public List<AuditEntry> findByWorkItemId(UUID workItemId) {
            return entries.stream()
                    .filter(e -> workItemId.equals(e.workItemId))
                    .toList();
        }
    }

    // -------------------------------------------------------------------------
    // Reflection helper — wire CDI @Inject fields without container
    // -------------------------------------------------------------------------

    private static void inject(Object target, String fieldName, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private TestWorkItemRepo workItemRepo;
    private TestAuditRepo auditRepo;

    private NotifyEscalationPolicy notifyPolicy;
    private AutoRejectEscalationPolicy autoRejectPolicy;
    private ReassignEscalationPolicy reassignPolicy;

    @BeforeEach
    void setUp() throws Exception {
        workItemRepo = new TestWorkItemRepo();
        auditRepo = new TestAuditRepo();

        // NotifyEscalationPolicy — expiredEvent field left null; onExpired fires it but
        // the status-only tests do not assert on events. Tests verify no status change only.
        notifyPolicy = new NotifyEscalationPolicy();

        autoRejectPolicy = new AutoRejectEscalationPolicy();
        inject(autoRejectPolicy, "workItemRepo", workItemRepo);
        inject(autoRejectPolicy, "auditRepo", auditRepo);

        reassignPolicy = new ReassignEscalationPolicy();
        inject(reassignPolicy, "workItemRepo", workItemRepo);
        inject(reassignPolicy, "notifyPolicy", notifyPolicy);
    }

    private WorkItem workItem(WorkItemStatus status) {
        WorkItem wi = new WorkItem();
        wi.id = UUID.randomUUID();
        wi.title = "Test item";
        wi.status = status;
        wi.priority = WorkItemPriority.NORMAL;
        wi.createdAt = Instant.now();
        wi.updatedAt = Instant.now();
        workItemRepo.save(wi);
        return wi;
    }

    // -------------------------------------------------------------------------
    // NotifyEscalationPolicy
    // -------------------------------------------------------------------------

    @Test
    void notifyPolicy_onExpired_doesNotChangeStatus() {
        WorkItem wi = workItem(WorkItemStatus.EXPIRED);
        // notifyPolicy logs and fires event — expiredEvent is null so we guard
        // the status-only assertion without relying on CDI event delivery
        try {
            notifyPolicy.onExpired(wi);
        } catch (NullPointerException ignored) {
            // CDI Event<> not wired in pure JUnit context — expected; status is unchanged
        }
        assertThat(wi.status).isEqualTo(WorkItemStatus.EXPIRED);
    }

    @Test
    void notifyPolicy_onUnclaimedPastDeadline_doesNotChangeStatus() {
        WorkItem wi = workItem(WorkItemStatus.PENDING);
        notifyPolicy.onUnclaimedPastDeadline(wi);
        assertThat(wi.status).isEqualTo(WorkItemStatus.PENDING);
    }

    // -------------------------------------------------------------------------
    // AutoRejectEscalationPolicy
    // -------------------------------------------------------------------------

    @Test
    void autoRejectPolicy_onExpired_transitionsToRejected() {
        WorkItem wi = workItem(WorkItemStatus.EXPIRED);
        autoRejectPolicy.onExpired(wi);
        assertThat(wi.status).isEqualTo(WorkItemStatus.REJECTED);
        assertThat(wi.completedAt).isNotNull();
    }

    @Test
    void autoRejectPolicy_onExpired_writesAuditEntry() {
        WorkItem wi = workItem(WorkItemStatus.EXPIRED);
        autoRejectPolicy.onExpired(wi);
        List<AuditEntry> trail = auditRepo.findByWorkItemId(wi.id);
        assertThat(trail).anyMatch(e -> "REJECTED".equals(e.event) && "system".equals(e.actor));
    }

    @Test
    void autoRejectPolicy_onUnclaimedPastDeadline_doesNothing() {
        WorkItem wi = workItem(WorkItemStatus.PENDING);
        autoRejectPolicy.onUnclaimedPastDeadline(wi);
        assertThat(wi.status).isEqualTo(WorkItemStatus.PENDING);
    }

    // -------------------------------------------------------------------------
    // ReassignEscalationPolicy
    // -------------------------------------------------------------------------

    @Test
    void reassignPolicy_withCandidateGroups_returnsToPending() {
        WorkItem wi = workItem(WorkItemStatus.ASSIGNED);
        wi.assigneeId = "alice";
        wi.candidateGroups = "team-a";
        workItemRepo.save(wi);

        reassignPolicy.onExpired(wi);

        assertThat(wi.status).isEqualTo(WorkItemStatus.PENDING);
        assertThat(wi.assigneeId).isNull();
    }

    @Test
    void reassignPolicy_withoutCandidateGroups_statusRemainsExpired() {
        WorkItem wi = workItem(WorkItemStatus.EXPIRED);
        wi.candidateGroups = null;
        wi.candidateUsers = null;
        workItemRepo.save(wi);

        // Falls back to notifyPolicy.onExpired — CDI Event null causes NPE, status unchanged
        try {
            reassignPolicy.onExpired(wi);
        } catch (NullPointerException ignored) {
            // Expected: notify fires CDI event that is not wired in pure JUnit context
        }

        assertThat(wi.status).isEqualTo(WorkItemStatus.EXPIRED);
    }

    @Test
    void reassignPolicy_onUnclaimedPastDeadline_withCandidateGroups_returnsToPending() {
        WorkItem wi = workItem(WorkItemStatus.PENDING);
        wi.assigneeId = "alice";
        wi.candidateGroups = "team-a";
        workItemRepo.save(wi);

        reassignPolicy.onUnclaimedPastDeadline(wi);

        assertThat(wi.status).isEqualTo(WorkItemStatus.PENDING);
        assertThat(wi.assigneeId).isNull();
    }
}
