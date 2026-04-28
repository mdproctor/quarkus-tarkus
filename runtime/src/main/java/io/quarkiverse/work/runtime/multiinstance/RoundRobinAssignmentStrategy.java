package io.quarkiverse.work.runtime.multiinstance;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import io.quarkiverse.work.api.AssignmentDecision;
import io.quarkiverse.work.api.InstanceAssignmentStrategy;
import io.quarkiverse.work.api.MultiInstanceContext;
import io.quarkiverse.work.api.SelectionContext;
import io.quarkiverse.work.api.WorkerSelectionStrategy;
import io.quarkiverse.work.runtime.model.WorkItem;

/**
 * Distributes instances across workers using the active {@link WorkerSelectionStrategy},
 * excluding already-assigned workers on each successive call.
 *
 * <p>
 * For each child instance, this strategy builds a {@link SelectionContext} with the
 * remaining (not yet assigned) candidate users and invokes the configured
 * {@code WorkerSelectionStrategy}. If the strategy returns no assignee, the child
 * falls back to the full parent candidate pool (claim-first).
 *
 * <p>
 * The exclusion set grows as instances are assigned, so each worker handles at most
 * one instance unless {@code allowSameAssignee} is configured in the parent (not
 * enforced here — enforced by the claim guard layer).
 */
@ApplicationScoped
@Named("roundRobin")
public class RoundRobinAssignmentStrategy implements InstanceAssignmentStrategy {

    private final WorkerSelectionStrategy workerSelectionStrategy;

    /** CDI constructor. */
    @Inject
    public RoundRobinAssignmentStrategy(final WorkerSelectionStrategy workerSelectionStrategy) {
        this.workerSelectionStrategy = workerSelectionStrategy;
    }

    /**
     * Assigns each instance by delegating to the active {@code WorkerSelectionStrategy},
     * filtering out workers already assigned to earlier instances in this batch.
     *
     * @param instances ordered list of child WorkItems, not yet persisted by this call
     * @param context parent WorkItem and resolved MultiInstanceConfig
     */
    @Override
    public void assign(final List<Object> instances, final MultiInstanceContext context) {
        final WorkItem parent = (WorkItem) context.parent();
        final Set<String> alreadyAssigned = new HashSet<>();

        for (final Object obj : instances) {
            final WorkItem child = (WorkItem) obj;
            final String filteredUsers = filterOut(parent.candidateUsers, alreadyAssigned);

            final SelectionContext selCtx = new SelectionContext(
                    child.category,
                    child.priority != null ? child.priority.name() : null,
                    child.requiredCapabilities,
                    parent.candidateGroups,
                    filteredUsers,
                    child.title,
                    child.description);

            final AssignmentDecision decision = workerSelectionStrategy.select(selCtx, List.of());
            if (decision != null && decision.assigneeId() != null) {
                child.assigneeId = decision.assigneeId();
                alreadyAssigned.add(decision.assigneeId());
            } else {
                child.candidateGroups = parent.candidateGroups;
                child.candidateUsers = parent.candidateUsers;
            }
        }
    }

    private String filterOut(final String candidateUsers, final Set<String> excluded) {
        if (candidateUsers == null || candidateUsers.isBlank()) {
            return candidateUsers;
        }
        return Arrays.stream(candidateUsers.split(","))
                .map(String::trim)
                .filter(u -> !excluded.contains(u))
                .collect(Collectors.joining(","));
    }
}
