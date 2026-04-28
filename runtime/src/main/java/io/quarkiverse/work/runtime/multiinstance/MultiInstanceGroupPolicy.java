package io.quarkiverse.work.runtime.multiinstance;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import io.quarkiverse.work.api.GroupStatus;
import io.quarkiverse.work.api.WorkItemGroupLifecycleEvent;
import io.quarkiverse.work.runtime.model.WorkItem;
import io.quarkiverse.work.runtime.model.WorkItemSpawnGroup;
import io.quarkiverse.work.runtime.model.WorkItemStatus;
import io.quarkiverse.work.runtime.service.WorkItemService;

@ApplicationScoped
public class MultiInstanceGroupPolicy {

    @Inject
    WorkItemService workItemService;

    @Inject
    Event<WorkItemGroupLifecycleEvent> groupEvent;

    /**
     * Update the spawn group counters and evaluate the M-of-N threshold.
     * Called from MultiInstanceCoordinator on @ObservesAsync.
     * Throws OptimisticLockException if concurrent update detected — caller retries.
     */
    @Transactional
    public void process(final WorkItem child) {
        final WorkItemSpawnGroup group = WorkItemSpawnGroup.findMultiInstanceByParentId(child.parentId);
        if (group == null)
            return;
        if (group.policyTriggered)
            return;

        if (child.status == WorkItemStatus.COMPLETED) {
            group.completedCount++;
        } else {
            group.rejectedCount++;
        }

        final int remaining = group.instanceCount - group.completedCount - group.rejectedCount;
        final int needed = group.requiredCount - group.completedCount;

        if (group.completedCount >= group.requiredCount) {
            resolve(group, GroupStatus.COMPLETED);
        } else if (remaining < needed) {
            resolve(group, GroupStatus.REJECTED);
        } else {
            fireGroupEvent(group, GroupStatus.IN_PROGRESS);
        }
    }

    private void resolve(final WorkItemSpawnGroup group, final GroupStatus outcome) {
        group.policyTriggered = true;

        if (outcome == GroupStatus.COMPLETED) {
            workItemService.complete(group.parentId, "system:multi-instance",
                    "threshold-met: " + group.completedCount + "/" + group.requiredCount);
        } else {
            workItemService.reject(group.parentId, "system:multi-instance",
                    "cannot-reach-threshold: " + group.rejectedCount + " rejections");
        }

        if ("CANCEL".equals(group.onThresholdReached)) {
            cancelRemainingChildren(group);
        }

        fireGroupEvent(group, outcome);
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

    private void fireGroupEvent(final WorkItemSpawnGroup group, final GroupStatus status) {
        final WorkItem parent = WorkItem.findById(group.parentId);
        groupEvent.fire(WorkItemGroupLifecycleEvent.of(
                group.parentId, group.id,
                group.instanceCount, group.requiredCount,
                group.completedCount, group.rejectedCount,
                status,
                parent != null ? parent.callerRef : null));
    }
}
