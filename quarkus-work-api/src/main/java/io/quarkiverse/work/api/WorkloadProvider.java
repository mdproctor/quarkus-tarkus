package io.quarkiverse.work.api;

/**
 * SPI for querying the active work count for a worker.
 *
 * <p>
 * Implement as {@code @ApplicationScoped @Alternative @Priority(1)} to provide
 * domain-specific workload data. quarkus-workitems provides {@code JpaWorkloadProvider}.
 * CaseHub provides its own implementation against its task store.
 *
 * <p>
 * Used by {@code WorkItemAssignmentService} to populate
 * {@link WorkerCandidate#activeWorkItemCount()} before passing candidates to
 * {@link WorkerSelectionStrategy#select}.
 */
@FunctionalInterface
public interface WorkloadProvider {

    /**
     * Returns the count of active (non-terminal) work units held by the given worker.
     *
     * @param workerId the worker identifier
     * @return active work count; 0 if unknown or no active items
     */
    int getActiveWorkCount(String workerId);
}
