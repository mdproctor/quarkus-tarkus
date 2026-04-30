package io.casehub.work.runtime.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.casehub.work.api.AssignmentDecision;
import io.casehub.work.api.AssignmentTrigger;
import io.casehub.work.api.SelectionContext;
import io.casehub.work.api.WorkerCandidate;
import io.casehub.work.api.WorkerRegistry;
import io.casehub.work.api.WorkerSelectionStrategy;
import io.casehub.work.api.WorkloadProvider;
import io.casehub.work.core.strategy.LeastLoadedStrategy;
import io.casehub.work.core.strategy.WorkBroker;
import io.casehub.work.runtime.model.*;

/**
 * Unit tests for WorkItemAssignmentService — no Quarkus boot.
 * Issue #116, Epics #100/#102.
 */
@ExtendWith(MockitoExtension.class)
class WorkItemAssignmentServiceTest {

    @Mock
    WorkloadProvider workloadProvider;
    @Mock
    WorkerRegistry workerRegistry;

    private WorkBroker workBroker;
    private WorkItemAssignmentService service;

    @BeforeEach
    void setUp() {
        workBroker = new WorkBroker();
        lenient().when(workerRegistry.resolveGroup(anyString())).thenReturn(List.of());
        lenient().when(workloadProvider.getActiveWorkCount(anyString())).thenReturn(0);
        service = new WorkItemAssignmentService(
                new LeastLoadedStrategy(), workerRegistry, workloadProvider, workBroker);
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
        service = new WorkItemAssignmentService(createdOnly, workerRegistry, workloadProvider, workBroker);

        final WorkItem wi = workItem(null, null, "alice,bob");
        service.assign(wi, AssignmentTrigger.RELEASED);
        assertThat(wi.assigneeId).isNull();
    }

    @Test
    void assign_fires_whenTriggerIsInStrategyTriggers() {
        when(workloadProvider.getActiveWorkCount("alice")).thenReturn(0);
        final WorkItem wi = workItem(null, null, "alice");
        service.assign(wi, AssignmentTrigger.CREATED);
        assertThat(wi.assigneeId).isEqualTo("alice");
    }

    // ── candidateUsers resolution ─────────────────────────────────────────────

    @Test
    void assign_parsesCandidateUsers_asCommaDelimitedList() {
        when(workloadProvider.getActiveWorkCount("alice")).thenReturn(5);
        when(workloadProvider.getActiveWorkCount("bob")).thenReturn(1);
        when(workloadProvider.getActiveWorkCount("carol")).thenReturn(3);
        final WorkItem wi = workItem(null, null, "alice,bob,carol");
        service.assign(wi, AssignmentTrigger.CREATED);
        assertThat(wi.assigneeId).isEqualTo("bob");
    }

    @Test
    void assign_trimsWhitespace_inCandidateUsers() {
        when(workloadProvider.getActiveWorkCount("alice")).thenReturn(0);
        when(workloadProvider.getActiveWorkCount("bob")).thenReturn(2);
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
        when(workloadProvider.getActiveWorkCount("alice")).thenReturn(3);
        when(workloadProvider.getActiveWorkCount("bob")).thenReturn(0);
        final WorkItem wi = workItem(null, "finance-team", null);
        service.assign(wi, AssignmentTrigger.CREATED);
        assertThat(wi.assigneeId).isEqualTo("bob");
    }

    @Test
    void assign_deduplicates_candidatesFromUsersAndGroups() {
        when(workerRegistry.resolveGroup("team")).thenReturn(
                List.of(WorkerCandidate.of("alice")));
        when(workloadProvider.getActiveWorkCount("alice")).thenReturn(2);
        when(workloadProvider.getActiveWorkCount("bob")).thenReturn(1);
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
        when(workloadProvider.getActiveWorkCount("alice")).thenReturn(0);
        final WorkItem wi = workItem(null, null, "alice");
        service.assign(wi, AssignmentTrigger.CREATED);
        assertThat(wi.assigneeId).isEqualTo("alice");
    }

    @Test
    void assign_setsCandidateGroups_fromNarrowDecision() {
        final WorkerSelectionStrategy narrower = (ctx, c) -> AssignmentDecision.narrowCandidates("narrowed-group", null);
        service = new WorkItemAssignmentService(narrower, workerRegistry, workloadProvider, workBroker);
        final WorkItem wi = workItem(null, "original-group", null);
        service.assign(wi, AssignmentTrigger.CREATED);
        assertThat(wi.candidateGroups).isEqualTo("narrowed-group");
        assertThat(wi.assigneeId).isNull();
    }

    @Test
    void assign_doesNotOverwrite_existingFields_onNoChange() {
        final WorkerSelectionStrategy noOp = (ctx, c) -> AssignmentDecision.noChange();
        service = new WorkItemAssignmentService(noOp, workerRegistry, workloadProvider, workBroker);
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
}
