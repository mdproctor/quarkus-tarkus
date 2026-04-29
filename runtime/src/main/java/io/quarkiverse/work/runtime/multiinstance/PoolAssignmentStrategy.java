package io.quarkiverse.work.runtime.multiinstance;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;

import io.quarkiverse.work.api.InstanceAssignmentStrategy;
import io.quarkiverse.work.api.MultiInstanceContext;
import io.quarkiverse.work.runtime.model.WorkItem;

/**
 * Copies the parent WorkItem's candidateGroups and candidateUsers to every child instance.
 *
 * <p>
 * This is the default strategy: all children are placed in the same candidate pool,
 * and whoever claims first gets the assignment. Suitable when any member of a team
 * can handle any instance.
 */
@ApplicationScoped
@Named("pool")
public class PoolAssignmentStrategy implements InstanceAssignmentStrategy {

    /**
     * Copies candidateGroups and candidateUsers from the parent WorkItem to all instances.
     *
     * @param instances ordered list of child WorkItems, not yet persisted by this call
     * @param context parent WorkItem and resolved MultiInstanceConfig
     */
    @Override
    public void assign(final List<Object> instances, final MultiInstanceContext context) {
        final WorkItem parent = (WorkItem) context.parent();
        // Only propagate candidateGroups/candidateUsers from parent when parent is a PARTICIPANT
        // (has its own candidateGroups). For COORDINATOR parents, children already have the
        // correct routing from their create request and must not be overwritten with null.
        if (parent.candidateGroups == null && parent.candidateUsers == null) {
            return;
        }
        for (final Object obj : instances) {
            final WorkItem child = (WorkItem) obj;
            child.candidateGroups = parent.candidateGroups;
            child.candidateUsers = parent.candidateUsers;
        }
    }
}
