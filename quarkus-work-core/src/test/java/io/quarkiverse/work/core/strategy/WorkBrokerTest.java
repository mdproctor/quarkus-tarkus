package io.casehub.work.core.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.work.api.AssignmentDecision;
import io.casehub.work.api.AssignmentTrigger;
import io.casehub.work.api.SelectionContext;
import io.casehub.work.api.WorkerCandidate;
import io.casehub.work.api.WorkerSelectionStrategy;

class WorkBrokerTest {

    private WorkBroker broker;

    @BeforeEach
    void setUp() {
        broker = new WorkBroker();
    }

    private SelectionContext ctx(final String caps) {
        return new SelectionContext("finance", "HIGH", caps, null, "alice,bob", null, null);
    }

    @Test
    void skipsWhenTriggerNotInStrategyTriggers() {
        final WorkerSelectionStrategy onlyCreated = new WorkerSelectionStrategy() {
            @Override
            public AssignmentDecision select(final SelectionContext c, final List<WorkerCandidate> cands) {
                return AssignmentDecision.assignTo("alice");
            }

            @Override
            public Set<AssignmentTrigger> triggers() {
                return Set.of(AssignmentTrigger.CREATED);
            }
        };
        final var cands = List.of(WorkerCandidate.of("alice"), WorkerCandidate.of("bob"));
        final var result = broker.apply(ctx(null), AssignmentTrigger.RELEASED, cands, onlyCreated);
        assertThat(result.isNoOp()).isTrue();
    }

    @Test
    void filtersOutCandidatesLackingRequiredCapabilities() {
        final WorkerSelectionStrategy strategy = (c, cands) -> AssignmentDecision.assignTo(cands.get(0).id());
        final var alice = new WorkerCandidate("alice", Set.of("approval", "legal"), 0);
        final var bob = new WorkerCandidate("bob", Set.of("approval"), 0);
        // require both approval + legal — only alice qualifies
        final var result = broker.apply(ctx("approval,legal"), AssignmentTrigger.CREATED,
                List.of(alice, bob), strategy);
        assertThat(result.assigneeId()).isEqualTo("alice");
    }

    @Test
    void noCapabilitiesFilter_passesAllCandidates() {
        final int[] callCount = { 0 };
        final WorkerSelectionStrategy strategy = (c, cands) -> {
            callCount[0] = cands.size();
            return AssignmentDecision.noChange();
        };
        final var cands = List.of(WorkerCandidate.of("alice"), WorkerCandidate.of("bob"));
        broker.apply(ctx(null), AssignmentTrigger.CREATED, cands, strategy);
        assertThat(callCount[0]).isEqualTo(2);
    }

    @Test
    void emptyCandidatesAfterCapabilityFilter_strategyReceivesEmptyList() {
        final int[] callCount = { 0 };
        final WorkerSelectionStrategy strategy = (c, cands) -> {
            callCount[0] = cands.size();
            return AssignmentDecision.noChange();
        };
        final var alice = new WorkerCandidate("alice", Set.of("approval"), 0); // missing "legal"
        broker.apply(ctx("approval,legal"), AssignmentTrigger.CREATED, List.of(alice), strategy);
        assertThat(callCount[0]).isZero();
    }

    @Test
    void blankCapabilities_treatedAsNoFilter() {
        final int[] callCount = { 0 };
        final WorkerSelectionStrategy strategy = (c, cands) -> {
            callCount[0] = cands.size();
            return AssignmentDecision.noChange();
        };
        broker.apply(ctx("  "), AssignmentTrigger.CREATED, List.of(WorkerCandidate.of("alice")), strategy);
        assertThat(callCount[0]).isEqualTo(1);
    }
}
