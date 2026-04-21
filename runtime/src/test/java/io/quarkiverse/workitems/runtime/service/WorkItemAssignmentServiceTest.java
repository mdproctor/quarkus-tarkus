package io.quarkiverse.workitems.runtime.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.quarkiverse.workitems.runtime.model.*;
import io.quarkiverse.workitems.runtime.repository.*;
import io.quarkiverse.workitems.spi.*;

/**
 * Unit tests for WorkItemAssignmentService — no Quarkus boot.
 * Issue #116, Epics #100/#102.
 */
@ExtendWith(MockitoExtension.class)
class WorkItemAssignmentServiceTest {

    @Mock
    WorkItemStore workItemStore;
    @Mock
    WorkerRegistry workerRegistry;

    private WorkItemAssignmentService service;

    @BeforeEach
    void setUp() {
        lenient().when(workerRegistry.resolveGroup(anyString())).thenReturn(List.of());
        lenient().when(workItemStore.scan(any())).thenReturn(List.of());
        service = new WorkItemAssignmentService(
                new LeastLoadedStrategy(), workerRegistry, workItemStore);
    }

    // ── Trigger filtering ─────────────────────────────────────────────────────

    @Test
    void assign_skipsWork_whenTriggerNotInStrategyTriggers() {
        final WorkerSelectionStrategy createdOnly = new WorkerSelectionStrategy() {
            @Override
            public AssignmentDecision select(SelectionContext c, List<WorkerCandidate> w) {
                return AssignmentDecision.assignTo("alice");
            }

            @Override
            public Set<AssignmentTrigger> triggers() {
                return Set.of(AssignmentTrigger.CREATED);
            }
        };
        service = new WorkItemAssignmentService(createdOnly, workerRegistry, workItemStore);

        final WorkItem wi = workItem(null, null, "alice,bob");
        service.assign(wi, AssignmentTrigger.RELEASED);
        assertThat(wi.assigneeId).isNull();
    }

    @Test
    void assign_fires_whenTriggerIsInStrategyTriggers() {
        when(workItemStore.scan(any())).thenReturn(List.of());
        final WorkItem wi = workItem(null, null, "alice");
        service.assign(wi, AssignmentTrigger.CREATED);
        assertThat(wi.assigneeId).isEqualTo("alice");
    }

    // ── candidateUsers resolution ─────────────────────────────────────────────

    @Test
    void assign_parsesCandidateUsers_asCommaDelimitedList() {
        mockActiveCount("alice", 5);
        mockActiveCount("bob", 1);
        mockActiveCount("carol", 3);
        final WorkItem wi = workItem(null, null, "alice,bob,carol");
        service.assign(wi, AssignmentTrigger.CREATED);
        assertThat(wi.assigneeId).isEqualTo("bob");
    }

    @Test
    void assign_trimsWhitespace_inCandidateUsers() {
        mockActiveCount("alice", 0);
        mockActiveCount("bob", 2);
        final WorkItem wi = workItem(null, null, " alice , bob ");
        service.assign(wi, AssignmentTrigger.CREATED);
        assertThat(wi.assigneeId).isEqualTo("alice");
    }

    @Test
    void assign_noChange_whenNoCandidateUsersAndNoGroupResolution() {
        final WorkItem wi = workItem(null, null, null);
        service.assign(wi, AssignmentTrigger.CREATED);
        assertThat(wi.assigneeId).isNull();
    }

    // ── candidateGroups resolution via WorkerRegistry ─────────────────────────

    @Test
    void assign_resolvesGroup_viaWorkerRegistry() {
        when(workerRegistry.resolveGroup("finance-team")).thenReturn(
                List.of(WorkerCandidate.of("alice"), WorkerCandidate.of("bob")));
        mockActiveCount("alice", 3);
        mockActiveCount("bob", 0);
        final WorkItem wi = workItem(null, "finance-team", null);
        service.assign(wi, AssignmentTrigger.CREATED);
        assertThat(wi.assigneeId).isEqualTo("bob");
    }

    @Test
    void assign_deduplicates_candidatesFromUsersAndGroups() {
        when(workerRegistry.resolveGroup("team")).thenReturn(
                List.of(WorkerCandidate.of("alice")));
        mockActiveCount("alice", 2);
        mockActiveCount("bob", 1);
        final WorkItem wi = workItem(null, "team", "alice,bob");
        service.assign(wi, AssignmentTrigger.CREATED);
        assertThat(wi.assigneeId).isEqualTo("bob");
    }

    // ── requiredCapabilities filtering ────────────────────────────────────────

    @Test
    void assign_filtersOut_candidatesFromRegistry_withoutRequiredCapabilities() {
        when(workerRegistry.resolveGroup("team")).thenReturn(List.of(
                new WorkerCandidate("alice", Set.of("audit", "legal"), 0),
                new WorkerCandidate("bob", Set.of("sales"), 0)));
        final WorkItem wi = workItem(null, "team", null);
        wi.requiredCapabilities = "audit";
        service.assign(wi, AssignmentTrigger.CREATED);
        assertThat(wi.assigneeId).isEqualTo("alice");
    }

    @Test
    void assign_returnsNoChange_whenAllCandidatesLackRequiredCapabilities() {
        when(workerRegistry.resolveGroup("team")).thenReturn(List.of(
                new WorkerCandidate("alice", Set.of("sales"), 0)));
        final WorkItem wi = workItem(null, "team", null);
        wi.requiredCapabilities = "audit";
        service.assign(wi, AssignmentTrigger.CREATED);
        assertThat(wi.assigneeId).isNull();
    }

    @Test
    void assign_candidateUsersHaveNoCapabilities_soAllFilteredWhenCapRequired() {
        // candidateUsers get WorkerCandidate.of() — empty capabilities
        final WorkItem wi = workItem(null, null, "alice,bob");
        wi.requiredCapabilities = "exotic-skill";
        service.assign(wi, AssignmentTrigger.CREATED);
        assertThat(wi.assigneeId).isNull();
    }

    // ── AssignmentDecision application ────────────────────────────────────────

    @Test
    void assign_setsAssigneeId_fromDecision() {
        mockActiveCount("alice", 0);
        final WorkItem wi = workItem(null, null, "alice");
        service.assign(wi, AssignmentTrigger.CREATED);
        assertThat(wi.assigneeId).isEqualTo("alice");
    }

    @Test
    void assign_setsCandidateGroups_fromNarrowDecision() {
        final WorkerSelectionStrategy narrower = (ctx, c) -> AssignmentDecision.narrowCandidates("narrowed-group", null);
        service = new WorkItemAssignmentService(narrower, workerRegistry, workItemStore);
        final WorkItem wi = workItem(null, "original-group", null);
        service.assign(wi, AssignmentTrigger.CREATED);
        assertThat(wi.candidateGroups).isEqualTo("narrowed-group");
        assertThat(wi.assigneeId).isNull();
    }

    @Test
    void assign_doesNotOverwrite_existingFields_onNoChange() {
        final WorkerSelectionStrategy noOp = (ctx, c) -> AssignmentDecision.noChange();
        service = new WorkItemAssignmentService(noOp, workerRegistry, workItemStore);
        final WorkItem wi = workItem(null, null, "alice");
        wi.assigneeId = "pre-existing";
        service.assign(wi, AssignmentTrigger.CREATED);
        assertThat(wi.assigneeId).isEqualTo("pre-existing");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private WorkItem workItem(final String groups, final String groupsOnly, final String users) {
        final WorkItem wi = new WorkItem();
        wi.id = UUID.randomUUID();
        wi.candidateGroups = groups != null ? groups : groupsOnly;
        wi.candidateUsers = users;
        return wi;
    }

    private void mockActiveCount(final String actorId, final int count) {
        final List<WorkItem> items = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            final WorkItem wi = new WorkItem();
            wi.assigneeId = actorId;
            wi.status = WorkItemStatus.ASSIGNED;
            items.add(wi);
        }
        lenient().when(workItemStore.scan(argThat(q -> q != null && actorId.equals(q.assigneeId()) &&
                q.statusIn() != null && q.statusIn().contains(WorkItemStatus.ASSIGNED))))
                .thenReturn(items);
    }
}
