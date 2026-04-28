package io.quarkiverse.work.api;

import java.util.List;

public interface InstanceAssignmentStrategy {
    /**
     * Assign candidate users/groups to each instance.
     * Implementations mutate instance fields (candidateGroups, candidateUsers, assigneeId).
     *
     * @param instances ordered list of child WorkItems, not yet persisted by this call
     * @param context parent WorkItem and resolved MultiInstanceConfig
     */
    void assign(List<Object> instances, MultiInstanceContext context);
}
