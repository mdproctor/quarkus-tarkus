package io.casehub.work.runtime.multiinstance;

import java.util.List;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import io.casehub.work.api.GroupStatus;
import io.casehub.work.api.WorkItemGroupLifecycleEvent;
import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.runtime.model.WorkItemSpawnGroup;
import io.casehub.work.runtime.model.WorkItemStatus;
import io.casehub.work.runtime.service.WorkItemService;

@ApplicationScoped
public class MultiInstanceGroupPolicy {

    @Inject
    WorkItemService workItemService;

    @Inject
    Event<WorkItemGroupLifecycleEvent> groupEvent;

    /**
     * Update the spawn group counters and evaluate the M-of-N threshold.
     * Called from MultiInstanceCoordinator on @ObservesAsync.
     *
     * <p>
     * Accepts the child's ID and terminal status rather than the entity itself to avoid
     * using a detached entity across transaction boundaries. The child is reloaded by ID
     * inside this transaction only when needed (e.g. to resolve parentId).
     *
     * <p>
     * Returns the {@link WorkItemGroupLifecycleEvent} that should be fired, or {@code null}
     * if nothing should be fired (e.g. policyTriggered guard hit). The caller fires the event
     * <em>after</em> this method returns — i.e. after the transaction commits — so that a
     * concurrent transaction that rolls back with OCC does not emit a spurious event.
     *
     * <p>
     * Throws on any transient failure (OCC, RollbackException) — caller retries once.
     * The {@code policyTriggered} guard makes this method idempotent on retry.
     */
    @Transactional
    public WorkItemGroupLifecycleEvent process(final UUID childId, final WorkItemStatus childStatus) {
        final WorkItem child = WorkItem.findById(childId);
        if (child == null)
            return null;

        final WorkItemSpawnGroup group = WorkItemSpawnGroup.findMultiInstanceByParentId(child.parentId);
        if (group == null)
            return null;
        if (group.policyTriggered)
            return null;

        if (childStatus == WorkItemStatus.COMPLETED) {
            group.completedCount++;
        } else {
            group.rejectedCount++;
        }

        final int remaining = group.instanceCount - group.completedCount - group.rejectedCount;
        final int needed = group.requiredCount - group.completedCount;

        if (group.completedCount >= group.requiredCount) {
            return resolve(group, GroupStatus.COMPLETED);
        } else if (remaining < needed) {
            return resolve(group, GroupStatus.REJECTED);
        } else {
            return buildGroupEvent(group, GroupStatus.IN_PROGRESS);
        }
    }

    /**
     * Fire a group lifecycle event. Called by the coordinator after the transaction commits.
     *
     * @param event the event to fire, or {@code null} (no-op)
     */
    public void fireEvent(final WorkItemGroupLifecycleEvent event) {
        if (event != null) {
            groupEvent.fireAsync(event);
        }
    }

    private WorkItemGroupLifecycleEvent resolve(final WorkItemSpawnGroup group, final GroupStatus outcome) {
        group.policyTriggered = true;

        if (outcome == GroupStatus.COMPLETED) {
            workItemService.completeFromSystem(group.parentId, "system:multi-instance",
                    "threshold-met: " + group.completedCount + "/" + group.requiredCount);
        } else {
            workItemService.rejectFromSystem(group.parentId, "system:multi-instance",
                    "cannot-reach-threshold: " + group.rejectedCount + " rejections");
        }

        if ("CANCEL".equals(group.onThresholdReached)) {
            cancelRemainingChildren(group);
        }

        return buildGroupEvent(group, outcome);
    }

    private void cancelRemainingChildren(final WorkItemSpawnGroup group) {
        final List<WorkItemStatus> terminalStatuses = List.of(
                WorkItemStatus.COMPLETED, WorkItemStatus.CANCELLED,
                WorkItemStatus.REJECTED, WorkItemStatus.EXPIRED);
        WorkItem.<WorkItem> list("parentId = ?1 AND status NOT IN ?2",
                group.parentId, terminalStatuses)
                .forEach(child -> workItemService.cancel(child.id, "system:multi-instance",
                        "threshold-met — cancelled by group policy"));
    }

    private WorkItemGroupLifecycleEvent buildGroupEvent(final WorkItemSpawnGroup group, final GroupStatus status) {
        final WorkItem parent = WorkItem.findById(group.parentId);
        return WorkItemGroupLifecycleEvent.of(
                group.parentId, group.id,
                group.instanceCount, group.requiredCount,
                group.completedCount, group.rejectedCount,
                status,
                parent != null ? parent.callerRef : null);
    }
}
