package io.quarkiverse.work.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Fires when a multi-instance WorkItem group changes aggregate state.
 *
 * Distinct from {@link WorkLifecycleEvent} (which fires per individual WorkItem transition).
 * Consumers subscribe to this for group-level outcomes: dashboards, notifications, CaseHub routing.
 * The {@code callerRef} is echoed from the parent WorkItem so CaseHub can route outcomes
 * without a query.
 */
public final class WorkItemGroupLifecycleEvent {

    private final UUID parentId;
    private final UUID groupId;
    private final int instanceCount;
    private final int requiredCount;
    private final int completedCount;
    private final int rejectedCount;
    private final GroupStatus groupStatus;
    private final String callerRef;
    private final Instant occurredAt;

    private WorkItemGroupLifecycleEvent(final UUID parentId, final UUID groupId,
            final int instanceCount, final int requiredCount,
            final int completedCount, final int rejectedCount,
            final GroupStatus groupStatus, final String callerRef) {
        this.parentId = parentId;
        this.groupId = groupId;
        this.instanceCount = instanceCount;
        this.requiredCount = requiredCount;
        this.completedCount = completedCount;
        this.rejectedCount = rejectedCount;
        this.groupStatus = groupStatus;
        this.callerRef = callerRef;
        this.occurredAt = Instant.now();
    }

    /**
     * Factory method for creating a WorkItemGroupLifecycleEvent.
     *
     * @param parentId the UUID of the parent WorkItem
     * @param groupId the UUID of the multi-instance group
     * @param instanceCount total number of instances in the group
     * @param requiredCount threshold count needed for resolution
     * @param completedCount number of instances that completed successfully
     * @param rejectedCount number of instances that were rejected
     * @param groupStatus the current {@link GroupStatus}
     * @param callerRef opaque caller reference echoed from parent WorkItem
     * @return a new WorkItemGroupLifecycleEvent
     */
    public static WorkItemGroupLifecycleEvent of(final UUID parentId, final UUID groupId,
            final int instanceCount, final int requiredCount,
            final int completedCount, final int rejectedCount,
            final GroupStatus groupStatus, final String callerRef) {
        return new WorkItemGroupLifecycleEvent(parentId, groupId, instanceCount, requiredCount,
                completedCount, rejectedCount, status, callerRef);
    }

    /**
     * @return the UUID of the parent WorkItem
     */
    public UUID parentId() {
        return parentId;
    }

    /**
     * @return the UUID of the multi-instance group
     */
    public UUID groupId() {
        return groupId;
    }

    /**
     * @return total number of instances in the group
     */
    public int instanceCount() {
        return instanceCount;
    }

    /**
     * @return threshold count needed for group resolution
     */
    public int requiredCount() {
        return requiredCount;
    }

    /**
     * @return number of instances that completed successfully
     */
    public int completedCount() {
        return completedCount;
    }

    /**
     * @return number of instances that were rejected
     */
    public int rejectedCount() {
        return rejectedCount;
    }

    /**
     * @return the current {@link GroupStatus} of the group
     */
    public GroupStatus groupStatus() {
        return groupStatus;
    }

    /**
     * @return opaque caller reference echoed from parent WorkItem for routing
     */
    public String callerRef() {
        return callerRef;
    }

    /**
     * @return the instant when this event occurred
     */
    public Instant occurredAt() {
        return occurredAt;
    }
}
