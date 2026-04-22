package io.quarkiverse.workitems.runtime.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.quarkiverse.work.api.AssignmentTrigger;
import io.quarkiverse.work.api.SelectionContext;
import io.quarkiverse.work.api.WorkerCandidate;
import io.quarkiverse.work.core.strategy.ClaimFirstStrategy;

class ClaimFirstStrategyTest {

    private final ClaimFirstStrategy strategy = new ClaimFirstStrategy();

    @Test
    void select_alwaysReturnsNoChange_withCandidates() {
        final List<WorkerCandidate> candidates = List.of(
                new WorkerCandidate("alice", Set.of(), 1),
                new WorkerCandidate("bob", Set.of(), 5));
        assertThat(strategy.select(ctx(), candidates).isNoOp()).isTrue();
    }

    @Test
    void select_alwaysReturnsNoChange_withNoCandidates() {
        assertThat(strategy.select(ctx(), List.of()).isNoOp()).isTrue();
    }

    @Test
    void triggers_includesAllThreeEvents() {
        assertThat(strategy.triggers()).containsExactlyInAnyOrder(AssignmentTrigger.values());
    }

    private SelectionContext ctx() {
        return new SelectionContext("cat", "NORMAL", null, null, "alice,bob", null, null);
    }
}
