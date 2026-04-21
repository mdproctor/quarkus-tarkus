package io.quarkiverse.workitems.runtime.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import io.quarkiverse.workitems.runtime.config.WorkItemsConfig;
import io.quarkiverse.workitems.runtime.model.WorkItem;
import io.quarkiverse.workitems.runtime.model.WorkItemStatus;
import io.quarkiverse.workitems.runtime.repository.WorkItemQuery;
import io.quarkiverse.workitems.runtime.repository.WorkItemStore;
import io.quarkiverse.workitems.spi.AssignmentDecision;
import io.quarkiverse.workitems.spi.AssignmentTrigger;
import io.quarkiverse.workitems.spi.SelectionContext;
import io.quarkiverse.workitems.spi.WorkerCandidate;
import io.quarkiverse.workitems.spi.WorkerRegistry;
import io.quarkiverse.workitems.spi.WorkerSelectionStrategy;

/**
 * Orchestrates worker selection for WorkItems on creation, release, and delegation.
 *
 * <p>
 * Flow:
 * <ol>
 * <li>Resolve active strategy (CDI {@code @Alternative} overrides config-selected built-in)</li>
 * <li>Check trigger against strategy's declared trigger set — skip if not in set</li>
 * <li>Build resolved candidate list from {@code candidateUsers} + {@code WorkerRegistry}</li>
 * <li>Populate {@code activeWorkItemCount} for each candidate from the WorkItem store</li>
 * <li>Filter candidates by {@code requiredCapabilities}</li>
 * <li>Call strategy, apply non-null fields of {@link AssignmentDecision} to WorkItem</li>
 * </ol>
 *
 * <p>
 * Mutates the WorkItem in memory only. The caller's {@code @Transactional} boundary
 * flushes the changes to the database.
 */
@ApplicationScoped
public class WorkItemAssignmentService {

    private static final List<WorkItemStatus> ACTIVE_STATUSES = List.of(
            WorkItemStatus.ASSIGNED, WorkItemStatus.IN_PROGRESS, WorkItemStatus.SUSPENDED);

    private final WorkItemStore workItemStore;
    private final WorkerRegistry workerRegistry;

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
     * @param workItemStore the store for active count queries
     * @param claimFirst the built-in claim-first strategy
     * @param leastLoaded the built-in least-loaded strategy
     */
    @Inject
    public WorkItemAssignmentService(
            final WorkItemsConfig config,
            final Instance<WorkerSelectionStrategy> alternatives,
            final WorkerRegistry workerRegistry,
            final WorkItemStore workItemStore,
            final ClaimFirstStrategy claimFirst,
            final LeastLoadedStrategy leastLoaded) {
        this.config = config;
        this.alternatives = alternatives;
        this.workerRegistry = workerRegistry;
        this.workItemStore = workItemStore;
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
     * @param workItemStore the store for active count queries
     */
    WorkItemAssignmentService(final WorkerSelectionStrategy strategy,
            final WorkerRegistry workerRegistry, final WorkItemStore workItemStore) {
        this.fixedStrategy = strategy;
        this.workerRegistry = workerRegistry;
        this.workItemStore = workItemStore;
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
        if (!strategy.triggers().contains(trigger)) {
            return;
        }

        final List<WorkerCandidate> candidates = resolveCandidates(workItem);
        final SelectionContext context = new SelectionContext(
                workItem.category,
                workItem.priority != null ? workItem.priority.name() : null,
                workItem.requiredCapabilities,
                workItem.candidateGroups,
                workItem.candidateUsers);

        final AssignmentDecision decision = strategy.select(context, candidates);
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
                            WorkerCandidate.of(id).withActiveWorkItemCount(countActive(id))));
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
                            : c.withActiveWorkItemCount(countActive(c.id())))
                    .forEach(candidates::add);
        }

        // 3. Filter by requiredCapabilities (AND logic — candidate must have ALL required)
        if (workItem.requiredCapabilities != null && !workItem.requiredCapabilities.isBlank()) {
            final Set<String> required = Arrays.stream(workItem.requiredCapabilities.split(","))
                    .map(String::trim).filter(s -> !s.isEmpty())
                    .collect(Collectors.toSet());
            candidates.removeIf(c -> !c.capabilities().containsAll(required));
        }

        return candidates;
    }

    private int countActive(final String actorId) {
        // Filter to strict assigneeId equality — WorkItemQuery.assigneeId also matches
        // candidateUsers LIKE, which would over-count unassigned pool items.
        return (int) workItemStore.scan(WorkItemQuery.builder()
                .assigneeId(actorId)
                .statusIn(ACTIVE_STATUSES)
                .build())
                .stream()
                .filter(wi -> actorId.equals(wi.assigneeId))
                .count();
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
