package io.casehub.work.core.strategy;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

import io.casehub.work.api.AssignmentDecision;
import io.casehub.work.api.SelectionContext;
import io.casehub.work.api.WorkerCandidate;
import io.casehub.work.api.WorkerSelectionStrategy;

/**
 * No-op worker selection strategy — leaves all WorkItems in the open pool.
 * Whoever claims first wins. Activated by:
 * {@code casehub.work.routing.strategy=claim-first}.
 */
@ApplicationScoped
public class ClaimFirstStrategy implements WorkerSelectionStrategy {

    @Override
    public AssignmentDecision select(final SelectionContext context,
            final List<WorkerCandidate> candidates) {
        return AssignmentDecision.noChange();
    }
}
