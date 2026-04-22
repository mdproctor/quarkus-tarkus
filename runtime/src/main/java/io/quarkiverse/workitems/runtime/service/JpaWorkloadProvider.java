package io.quarkiverse.workitems.runtime.service;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkiverse.work.api.WorkloadProvider;
import io.quarkiverse.workitems.runtime.model.WorkItemStatus;
import io.quarkiverse.workitems.runtime.repository.WorkItemQuery;
import io.quarkiverse.workitems.runtime.repository.WorkItemStore;

/**
 * WorkloadProvider backed by the JPA WorkItemStore.
 * Counts ASSIGNED, IN_PROGRESS, and SUSPENDED WorkItems for a given actor.
 */
@ApplicationScoped
public class JpaWorkloadProvider implements WorkloadProvider {

    private static final List<WorkItemStatus> ACTIVE_STATUSES = List.of(
            WorkItemStatus.ASSIGNED, WorkItemStatus.IN_PROGRESS, WorkItemStatus.SUSPENDED);

    private final WorkItemStore workItemStore;

    @Inject
    public JpaWorkloadProvider(final WorkItemStore workItemStore) {
        this.workItemStore = workItemStore;
    }

    /**
     * Returns the count of active (non-terminal) WorkItems held by the given worker.
     * Active means ASSIGNED, IN_PROGRESS, or SUSPENDED — states where the worker
     * is currently responsible for the work unit.
     *
     * @param workerId the worker identifier
     * @return active WorkItem count; 0 if none
     */
    @Override
    public int getActiveWorkCount(final String workerId) {
        return (int) workItemStore.scan(WorkItemQuery.builder()
                .assigneeId(workerId)
                .statusIn(ACTIVE_STATUSES)
                .build())
                .stream()
                .filter(wi -> workerId.equals(wi.assigneeId))
                .count();
    }
}
