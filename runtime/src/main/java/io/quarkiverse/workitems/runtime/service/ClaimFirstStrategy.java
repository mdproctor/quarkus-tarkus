package io.quarkiverse.workitems.runtime.service;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.workitems.spi.AssignmentDecision;
import io.quarkiverse.workitems.spi.SelectionContext;
import io.quarkiverse.workitems.spi.WorkerCandidate;
import io.quarkiverse.workitems.spi.WorkerSelectionStrategy;

/**
 * No-op worker selection strategy — leaves all WorkItems in the open pool.
 * Whoever claims first wins. Activated by:
 * {@code quarkus.workitems.routing.strategy=claim-first}.
 */
@ApplicationScoped
public class ClaimFirstStrategy implements WorkerSelectionStrategy {

    @Override
    public AssignmentDecision select(final SelectionContext context,
            final List<WorkerCandidate> candidates) {
        return AssignmentDecision.noChange();
    }
}
