package io.casehub.work.core.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.casehub.work.api.AssignmentTrigger;
import io.casehub.work.api.SelectionContext;
import io.casehub.work.api.WorkerCandidate;

class LeastLoadedStrategyTest {

    private final LeastLoadedStrategy strategy = new LeastLoadedStrategy();
    private final SelectionContext ctx = new SelectionContext(null, null, null, null, null, null, null);

    @Test
    void selectsCandidateWithFewestActiveItems() {
        final var alice = new WorkerCandidate("alice", Set.of(), 5);
        final var bob = new WorkerCandidate("bob", Set.of(), 1);
        final var result = strategy.select(ctx, List.of(alice, bob));
        assertThat(result.assigneeId()).isEqualTo("bob");
    }

    @Test
    void emptyCandidates_returnsNoChange() {
        assertThat(strategy.select(ctx, List.of()).isNoOp()).isTrue();
    }

    @Test
    void singleCandidate_selected() {
        final var result = strategy.select(ctx, List.of(WorkerCandidate.of("alice")));
        assertThat(result.assigneeId()).isEqualTo("alice");
    }

    @Test
    void tiedCandidates_picksOne() {
        final var alice = new WorkerCandidate("alice", Set.of(), 2);
        final var bob = new WorkerCandidate("bob", Set.of(), 2);
        final var result = strategy.select(ctx, List.of(alice, bob));
        assertThat(result.assigneeId()).isIn("alice", "bob");
    }

    @Test
    void triggersDefaultsToAllThree() {
        assertThat(strategy.triggers()).containsExactlyInAnyOrder(AssignmentTrigger.values());
    }
}
