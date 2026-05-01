package io.casehub.work.runtime.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.work.api.ClaimSlaContext;
import io.casehub.work.api.ClaimSlaPolicy;
import io.casehub.work.api.WorkLifecycleEvent;
import io.casehub.work.runtime.model.AuditEntry;
import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.runtime.model.WorkItemStatus;
import io.casehub.work.runtime.repository.AuditEntryStore;
import io.casehub.work.runtime.repository.WorkItemQuery;
import io.casehub.work.runtime.repository.WorkItemStore;

/**
 * Pure unit tests for {@link ExpiryLifecycleService} — no Quarkus, no CDI.
 *
 * <p>
 * Verifies that expiry processing and claim deadline breach handling correctly
 * transition WorkItem state, write audit entries, and invoke escalation policy.
 */
class ExpiryLifecycleServiceTest {

    // ── In-memory stubs ───────────────────────────────────────────────────────

    static class TestStore implements WorkItemStore {
        final Map<UUID, WorkItem> items = new ConcurrentHashMap<>();

        @Override
        public WorkItem put(final WorkItem wi) {
            if (wi.id == null) wi.id = UUID.randomUUID();
            items.put(wi.id, wi);
            return wi;
        }

        @Override
        public Optional<WorkItem> get(final UUID id) {
            return Optional.ofNullable(items.get(id));
        }

        @Override
        public List<WorkItem> scan(final WorkItemQuery query) {
            return items.values().stream()
                    .filter(wi -> {
                        if (query.expiresAtOrBefore() != null) {
                            return wi.expiresAt != null
                                    && !wi.expiresAt.isAfter(query.expiresAtOrBefore())
                                    && !wi.status.isTerminal();
                        }
                        if (query.claimDeadlineOrBefore() != null) {
                            return wi.claimDeadline != null
                                    && !wi.claimDeadline.isAfter(query.claimDeadlineOrBefore())
                                    && wi.status == WorkItemStatus.PENDING;
                        }
                        return false;
                    })
                    .toList();
        }
    }

    static class TestAuditStore implements AuditEntryStore {
        final List<AuditEntry> entries = new ArrayList<>();

        @Override public void append(final AuditEntry e) { entries.add(e); }
        @Override public List<AuditEntry> findByWorkItemId(final UUID id) {
            return entries.stream().filter(e -> id.equals(e.workItemId)).toList();
        }
        @Override public List<AuditEntry> query(final io.casehub.work.runtime.repository.AuditQuery q) {
            return List.of();
        }
        @Override public long count(final io.casehub.work.runtime.repository.AuditQuery q) {
            return 0;
        }
    }

    /** Always returns 4 hours from now as the next claim deadline. */
    static class FixedClaimSlaPolicy implements ClaimSlaPolicy {
        @Override
        public Instant computePoolDeadline(final ClaimSlaContext ctx) {
            return Instant.now().plus(4, ChronoUnit.HOURS);
        }
    }

    // ── Setup ─────────────────────────────────────────────────────────────────

    private TestStore store;
    private TestAuditStore auditStore;
    private final List<WorkLifecycleEvent> escalated = new ArrayList<>();
    private ExpiryLifecycleService service;

    @BeforeEach
    void setUp() {
        store = new TestStore();
        auditStore = new TestAuditStore();
        escalated.clear();

        service = new ExpiryLifecycleService();
        service.workItemStore = store;
        service.auditStore = auditStore;
        service.escalationPolicy = escalated::add;
        service.claimEscalationPolicy = escalated::add;
        service.lifecycleEvent = null; // not under test
        service.claimSlaPolicy = new FixedClaimSlaPolicy();
        service.config = WorkItemServiceTest.testConfig();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private WorkItem expiredItem() {
        final WorkItem wi = new WorkItem();
        wi.id = UUID.randomUUID();
        wi.status = WorkItemStatus.PENDING;
        wi.expiresAt = Instant.now().minus(1, ChronoUnit.HOURS);
        wi.createdAt = Instant.now().minus(2, ChronoUnit.HOURS);
        wi.accumulatedUnclaimedSeconds = 0L;
        store.put(wi);
        return wi;
    }

    private WorkItem claimExpiredItem() {
        final WorkItem wi = new WorkItem();
        wi.id = UUID.randomUUID();
        wi.status = WorkItemStatus.PENDING;
        wi.claimDeadline = Instant.now().minus(1, ChronoUnit.HOURS);
        wi.createdAt = Instant.now().minus(2, ChronoUnit.HOURS);
        wi.lastReturnedToPoolAt = Instant.now().minus(2, ChronoUnit.HOURS);
        wi.accumulatedUnclaimedSeconds = 0L;
        store.put(wi);
        return wi;
    }

    // ── checkExpired ──────────────────────────────────────────────────────────

    @Test
    void checkExpired_transitionsToExpired() {
        final WorkItem wi = expiredItem();
        service.checkExpired();
        assertThat(store.get(wi.id).orElseThrow().status).isEqualTo(WorkItemStatus.EXPIRED);
    }

    @Test
    void checkExpired_setsCompletedAt() {
        final WorkItem wi = expiredItem();
        service.checkExpired();
        assertThat(store.get(wi.id).orElseThrow().completedAt).isNotNull();
    }

    @Test
    void checkExpired_writesExpiredAuditEntry() {
        final WorkItem wi = expiredItem();
        service.checkExpired();
        assertThat(auditStore.findByWorkItemId(wi.id))
                .anyMatch(e -> "EXPIRED".equals(e.event) && "system".equals(e.actor));
    }

    @Test
    void checkExpired_invokesEscalationPolicy() {
        expiredItem();
        service.checkExpired();
        assertThat(escalated).isNotEmpty();
    }

    @Test
    void checkExpired_skipsAlreadyTerminalItems() {
        final WorkItem wi = expiredItem();
        wi.status = WorkItemStatus.COMPLETED;
        store.put(wi);
        service.checkExpired();
        assertThat(store.get(wi.id).orElseThrow().status).isEqualTo(WorkItemStatus.COMPLETED);
    }

    @Test
    void checkExpired_processesMultipleItems() {
        expiredItem();
        expiredItem();
        service.checkExpired();
        final long expiredCount = store.items.values().stream()
                .filter(wi -> wi.status == WorkItemStatus.EXPIRED).count();
        assertThat(expiredCount).isEqualTo(2);
    }

    // ── checkClaimDeadlines ───────────────────────────────────────────────────

    @Test
    void checkClaimDeadlines_accumulatesUnclaimedTime() {
        final WorkItem wi = claimExpiredItem();
        service.checkClaimDeadlines();
        assertThat(store.get(wi.id).orElseThrow().accumulatedUnclaimedSeconds).isGreaterThan(0);
    }

    @Test
    void checkClaimDeadlines_resetsClaimDeadlineToFuture() {
        final WorkItem wi = claimExpiredItem();
        service.checkClaimDeadlines();
        assertThat(store.get(wi.id).orElseThrow().claimDeadline).isAfter(Instant.now());
    }

    @Test
    void checkClaimDeadlines_invokesClaimEscalationPolicy() {
        claimExpiredItem();
        service.checkClaimDeadlines();
        assertThat(escalated).isNotEmpty();
    }

    @Test
    void checkClaimDeadlines_doesNotChangeStatus() {
        final WorkItem wi = claimExpiredItem();
        service.checkClaimDeadlines();
        assertThat(store.get(wi.id).orElseThrow().status).isEqualTo(WorkItemStatus.PENDING);
    }

    @Test
    void checkClaimDeadlines_updatesLastReturnedToPoolAt() {
        final WorkItem wi = claimExpiredItem();
        final Instant before = wi.lastReturnedToPoolAt;
        service.checkClaimDeadlines();
        assertThat(store.get(wi.id).orElseThrow().lastReturnedToPoolAt).isAfter(before);
    }
}
