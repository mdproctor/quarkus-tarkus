package io.quarkiverse.workitems.runtime.service;

import java.util.Comparator;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.workitems.spi.AssignmentDecision;
import io.quarkiverse.workitems.spi.SelectionContext;
import io.quarkiverse.workitems.spi.WorkerCandidate;
import io.quarkiverse.workitems.spi.WorkerSelectionStrategy;

/**
 * Pre-assigns WorkItems to the candidate with the fewest active WorkItems.
 *
 * <p>
 * "Active" means ASSIGNED, IN_PROGRESS, or SUSPENDED — states where a worker
 * is actively holding a WorkItem. PENDING items are not counted (no one has claimed them).
 *
 * <p>
 * When the candidate list is empty, returns {@link AssignmentDecision#noChange()}
 * and the WorkItem remains in the open pool for claim-first behaviour.
 *
 * <p>
 * Activated by: {@code quarkus.workitems.routing.strategy=least-loaded} (default).
 */
@ApplicationScoped
public class LeastLoadedStrategy implements WorkerSelectionStrategy {

    @Override
    public AssignmentDecision select(final SelectionContext context,
            final List<WorkerCandidate> candidates) {
        if (candidates.isEmpty()) {
            return AssignmentDecision.noChange();
        }
        return candidates.stream()
                .min(Comparator.comparingInt(WorkerCandidate::activeWorkItemCount))
                .map(c -> AssignmentDecision.assignTo(c.id()))
                .orElse(AssignmentDecision.noChange());
    }
}
