package io.casehub.work.core.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.casehub.work.api.SelectionContext;
import io.casehub.work.api.WorkerCandidate;

class ClaimFirstStrategyTest {

    private final ClaimFirstStrategy strategy = new ClaimFirstStrategy();
    private final SelectionContext ctx = new SelectionContext(null, null, null, null, null, null, null);

    @Test
    void alwaysReturnsNoChange() {
        final var result = strategy.select(ctx, List.of(WorkerCandidate.of("alice")));
        assertThat(result.isNoOp()).isTrue();
    }

    @Test
    void emptyList_returnsNoChange() {
        assertThat(strategy.select(ctx, List.of()).isNoOp()).isTrue();
    }
}
