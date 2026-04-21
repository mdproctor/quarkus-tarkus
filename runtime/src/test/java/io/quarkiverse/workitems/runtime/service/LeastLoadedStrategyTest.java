package io.quarkiverse.workitems.runtime.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.quarkiverse.workitems.spi.*;

class LeastLoadedStrategyTest {

    private final LeastLoadedStrategy strategy = new LeastLoadedStrategy();

    @Test
    void select_assignsToCandidate_withLowestActiveCount() {
        final List<WorkerCandidate> candidates = List.of(
                new WorkerCandidate("alice", Set.of(), 5),
                new WorkerCandidate("bob", Set.of(), 1),
                new WorkerCandidate("carol", Set.of(), 3));
        assertThat(strategy.select(ctx(), candidates).assigneeId()).isEqualTo("bob");
    }

    @Test
    void select_returnsNoChange_whenCandidateListIsEmpty() {
        assertThat(strategy.select(ctx(), List.of()).isNoOp()).isTrue();
    }

    @Test
    void select_assignsToOnlyCandidate_whenListHasOne() {
        final List<WorkerCandidate> candidates = List.of(
                new WorkerCandidate("alice", Set.of(), 0));
        assertThat(strategy.select(ctx(), candidates).assigneeId()).isEqualTo("alice");
    }

    @Test
    void select_picksOneAmongTies_whenMultipleCandidatesHaveSameCount() {
        final List<WorkerCandidate> candidates = List.of(
                new WorkerCandidate("alice", Set.of(), 2),
                new WorkerCandidate("bob", Set.of(), 2));
        assertThat(strategy.select(ctx(), candidates).assigneeId()).isIn("alice", "bob");
    }

    @Test
    void select_assignsToZeroLoadCandidate_overHighLoad() {
        final List<WorkerCandidate> candidates = List.of(
                new WorkerCandidate("busy", Set.of(), 20),
                new WorkerCandidate("free", Set.of(), 0));
        assertThat(strategy.select(ctx(), candidates).assigneeId()).isEqualTo("free");
    }

    @Test
    void select_candidateGroups_andCandidateUsers_inDecision_areNull() {
        final List<WorkerCandidate> candidates = List.of(
                new WorkerCandidate("alice", Set.of(), 0));
        final AssignmentDecision d = strategy.select(ctx(), candidates);
        assertThat(d.candidateGroups()).isNull();
        assertThat(d.candidateUsers()).isNull();
    }

    @Test
    void triggers_includesAllThreeEvents() {
        assertThat(strategy.triggers()).containsExactlyInAnyOrder(AssignmentTrigger.values());
    }

    private SelectionContext ctx() {
        return new SelectionContext("cat", "NORMAL", null, null, "alice,bob");
    }
}
