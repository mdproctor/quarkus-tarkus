package io.casehub.work.runtime.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import io.casehub.work.api.AssignmentDecision;
import io.casehub.work.api.AssignmentTrigger;
import io.casehub.work.api.SelectionContext;
import io.casehub.work.api.WorkerCandidate;
import io.casehub.work.api.WorkerRegistry;
import io.casehub.work.api.WorkerSelectionStrategy;
import io.casehub.work.api.WorkloadProvider;
import io.casehub.work.core.strategy.ClaimFirstStrategy;
import io.casehub.work.core.strategy.LeastLoadedStrategy;
import io.casehub.work.core.strategy.WorkBroker;
import io.casehub.work.runtime.config.WorkItemsConfig;
import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.runtime.model.WorkItemStatus;

/**
 * Orchestrates worker selection for WorkItems on creation, release, and delegation.
 *
 * <p>
 * Flow:
 * <ol>
 * <li>Resolve active strategy (CDI {@code @Alternative} overrides config-selected built-in)</li>
 * <li>Build resolved candidate list from {@code candidateUsers} + {@code WorkerRegistry}</li>
 * <li>Populate {@code activeWorkItemCount} for each candidate via {@link WorkloadProvider}</li>
 * <li>Delegate trigger gating, capability filtering, and strategy dispatch to {@link WorkBroker}</li>
 * <li>Apply non-null fields of {@link AssignmentDecision} to the WorkItem</li>
 * </ol>
 *
 * <p>
 * Mutates the WorkItem in memory only. The caller's {@code @Transactional} boundary
 * flushes the changes to the database.
 */
@ApplicationScoped
public class WorkItemAssignmentService {

    private final WorkerRegistry workerRegistry;
    private final WorkloadProvider workloadProvider;
    private final WorkBroker workBroker;

    // CDI-wired fields — null in unit-test constructor
    private WorkItemsConfig config;
    private Instance<WorkerSelectionStrategy> alternatives;
    private ClaimFirstStrategy claimFirst;
    private LeastLoadedStrategy leastLoaded;

    // Resolved at construction time for the package-private test constructor
    private final WorkerSelectionStrategy fixedStrategy;

    /**
     * CDI constructor — full wiring with config and @Alternative discovery.
     *
     * @param config the WorkItems configuration
     * @param alternatives CDI instances of alternative strategies
     * @param workerRegistry the worker registry for group resolution
     * @param workloadProvider the workload provider for active count queries
     * @param workBroker the generic work assignment broker
     * @param claimFirst the built-in claim-first strategy
     * @param leastLoaded the built-in least-loaded strategy
     */
    @Inject
    public WorkItemAssignmentService(
            final WorkItemsConfig config,
            final Instance<WorkerSelectionStrategy> alternatives,
            final WorkerRegistry workerRegistry,
            final WorkloadProvider workloadProvider,
            final WorkBroker workBroker,
            final ClaimFirstStrategy claimFirst,
            final LeastLoadedStrategy leastLoaded) {
        this.config = config;
        this.alternatives = alternatives;
        this.workerRegistry = workerRegistry;
        this.workloadProvider = workloadProvider;
        this.workBroker = workBroker;
        this.claimFirst = claimFirst;
        this.leastLoaded = leastLoaded;
        this.fixedStrategy = null;
    }

    /**
     * Package-private constructor for unit tests — bypasses CDI and config.
     * The provided strategy is used directly with no @Alternative lookup.
     *
     * @param strategy the strategy to use directly
     * @param workerRegistry the worker registry for group resolution
     * @param workloadProvider the workload provider for active count queries
     * @param workBroker the generic work assignment broker
     */
    WorkItemAssignmentService(final WorkerSelectionStrategy strategy,
            final WorkerRegistry workerRegistry,
            final WorkloadProvider workloadProvider,
            final WorkBroker workBroker) {
        this.fixedStrategy = strategy;
        this.workerRegistry = workerRegistry;
        this.workloadProvider = workloadProvider;
        this.workBroker = workBroker;
    }

    /**
     * Apply the active strategy to the WorkItem for the given trigger event.
     * Mutates the WorkItem fields in memory; caller persists.
     *
     * @param workItem the WorkItem to assign
     * @param trigger the lifecycle event that triggered this assignment attempt
     */
    public void assign(final WorkItem workItem, final AssignmentTrigger trigger) {
        final WorkerSelectionStrategy strategy = activeStrategy();
        final List<WorkerCandidate> candidates = resolveCandidates(workItem);
        final SelectionContext context = new SelectionContext(
                workItem.category,
                workItem.priority != null ? workItem.priority.name() : null,
                workItem.requiredCapabilities,
                workItem.candidateGroups,
                workItem.candidateUsers,
                workItem.title,
                workItem.description);

        final AssignmentDecision decision = workBroker.apply(context, trigger, candidates, strategy);
        applyDecision(workItem, decision);
    }

    private WorkerSelectionStrategy activeStrategy() {
        if (fixedStrategy != null) {
            return fixedStrategy; // unit-test path
        }
        // CDI @Alternative overrides config (excluding the built-in beans themselves)
        if (alternatives != null) {
            final var alt = alternatives.stream()
                    .filter(s -> !(s instanceof ClaimFirstStrategy)
                            && !(s instanceof LeastLoadedStrategy))
                    .findFirst();
            if (alt.isPresent()) {
                return alt.get();
            }
        }
        return "claim-first".equals(config.routing().strategy()) ? claimFirst : leastLoaded;
    }

    private List<WorkerCandidate> resolveCandidates(final WorkItem workItem) {
        final List<WorkerCandidate> candidates = new ArrayList<>();

        // 1. candidateUsers — direct user IDs
        if (workItem.candidateUsers != null && !workItem.candidateUsers.isBlank()) {
            Arrays.stream(workItem.candidateUsers.split(","))
                    .map(String::trim)
                    .filter(id -> !id.isEmpty())
                    .forEach(id -> candidates.add(
                            WorkerCandidate.of(id).withActiveWorkItemCount(
                                    workloadProvider.getActiveWorkCount(id))));
        }

        // 2. candidateGroups — resolved via WorkerRegistry
        if (workItem.candidateGroups != null && !workItem.candidateGroups.isBlank()) {
            Arrays.stream(workItem.candidateGroups.split(","))
                    .map(String::trim)
                    .filter(g -> !g.isEmpty())
                    .flatMap(g -> workerRegistry.resolveGroup(g).stream())
                    .filter(c -> candidates.stream().noneMatch(e -> e.id().equals(c.id())))
                    .map(c -> c.activeWorkItemCount() > 0
                            ? c
                            : c.withActiveWorkItemCount(workloadProvider.getActiveWorkCount(c.id())))
                    .forEach(candidates::add);
        }

        // Capability filtering and trigger gating are handled by WorkBroker.apply()
        return candidates;
    }

    private void applyDecision(final WorkItem workItem, final AssignmentDecision decision) {
        if (decision.assigneeId() != null) {
            workItem.assigneeId = decision.assigneeId();
            workItem.status = WorkItemStatus.ASSIGNED;
            workItem.assignedAt = Instant.now();
        }
        if (decision.candidateGroups() != null) {
            workItem.candidateGroups = decision.candidateGroups();
        }
        if (decision.candidateUsers() != null) {
            workItem.candidateUsers = decision.candidateUsers();
        }
    }
}
