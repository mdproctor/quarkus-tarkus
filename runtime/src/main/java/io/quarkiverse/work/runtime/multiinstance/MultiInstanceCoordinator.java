package io.quarkiverse.work.runtime.multiinstance;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import jakarta.persistence.OptimisticLockException;

import io.quarkiverse.work.runtime.event.WorkItemLifecycleEvent;
import io.quarkiverse.work.runtime.model.WorkItem;

/**
 * Observes terminal {@link WorkItemLifecycleEvent} instances asynchronously and
 * delegates to {@link MultiInstanceGroupPolicy} to update group counters and
 * evaluate the M-of-N threshold.
 *
 * <p>
 * Using {@link ObservesAsync} ensures the child WorkItem's transaction is already
 * committed before the coordinator runs, so the policy sees consistent data.
 * {@link MultiInstanceGroupPolicy#process} is {@code @Transactional} and handles
 * its own transaction boundary.
 *
 * <p>
 * A single retry on {@link OptimisticLockException} handles the rare case where
 * two siblings complete concurrently and contend on the spawn-group version column.
 */
@ApplicationScoped
public class MultiInstanceCoordinator {

    @Inject
    MultiInstanceGroupPolicy policy;

    /**
     * Receives every terminal WorkItem lifecycle event asynchronously.
     * Skips events for WorkItems that have no parent (not part of a multi-instance group).
     *
     * @param event the lifecycle event carrying the child WorkItem as its source
     */
    void onChildTerminal(@ObservesAsync WorkItemLifecycleEvent event) {
        final WorkItem child = (WorkItem) event.source();
        if (child.parentId == null)
            return;
        if (!child.status.isTerminal())
            return;

        int attempt = 0;
        while (attempt < 2) {
            try {
                policy.process(child);
                return;
            } catch (OptimisticLockException e) {
                attempt++;
            }
        }
    }
}
