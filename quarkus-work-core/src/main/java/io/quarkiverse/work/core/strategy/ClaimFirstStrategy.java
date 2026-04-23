package io.quarkiverse.work.core.strategy;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.work.api.AssignmentDecision;
import io.quarkiverse.work.api.SelectionContext;
import io.quarkiverse.work.api.WorkerCandidate;
import io.quarkiverse.work.api.WorkerSelectionStrategy;

/**
 * No-op worker selection strategy — leaves all WorkItems in the open pool.
 * Whoever claims first wins. Activated by:
 * {@code quarkus.work.routing.strategy=claim-first}.
 */
@ApplicationScoped
public class ClaimFirstStrategy implements WorkerSelectionStrategy {

    @Override
    public AssignmentDecision select(final SelectionContext context,
            final List<WorkerCandidate> candidates) {
        return AssignmentDecision.noChange();
    }
}
