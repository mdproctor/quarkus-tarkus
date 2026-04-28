package io.quarkiverse.work.runtime.multiinstance;

import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;

import io.quarkiverse.work.api.WorkItemGroupLifecycleEvent;
import io.quarkiverse.work.runtime.event.WorkItemLifecycleEvent;
import io.quarkiverse.work.runtime.model.WorkItem;
import io.quarkiverse.work.runtime.model.WorkItemStatus;

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
 * Group lifecycle events are fired <em>after</em> {@code process()} returns — i.e.
 * after the transaction commits — so that a concurrent transaction that rolls back
 * with OCC does not emit a spurious event.
 *
 * <p>
 * A single retry handles the rare case where two siblings complete concurrently
 * and contend on the spawn-group version column. In Quarkus/Narayana JTA, OCC
 * detected at commit time propagates as {@code RollbackException} wrapping
 * {@code OptimisticLockException} — catching the broad {@link Exception} type
 * ensures the retry fires regardless of how the JTA layer wraps the failure.
 * On the second attempt {@code policyTriggered=true} makes {@code process()}
 * return {@code null}, so the retry is safe even if the first attempt partially succeeded.
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

        final UUID childId = child.id;
        final WorkItemStatus childStatus = child.status;

        WorkItemGroupLifecycleEvent groupEvent = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                groupEvent = policy.process(childId, childStatus);
                break;
            } catch (Exception e) {
                // Retry once on any transient failure (OCC, RollbackException, etc.).
                // On the second attempt policyTriggered=true makes process() return null (idempotent).
            }
        }
        // Fire the group event only after the transaction commits to prevent spurious
        // events from transactions that subsequently roll back due to OCC.
        policy.fireEvent(groupEvent);
    }
}
