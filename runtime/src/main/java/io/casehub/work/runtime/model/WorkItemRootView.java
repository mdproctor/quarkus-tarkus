package io.casehub.work.runtime.model;

import io.casehub.work.api.GroupStatus;

/**
 * Projection of a root WorkItem (parentId IS NULL) enriched with
 * aggregate stats for the threaded inbox view.
 */
public record WorkItemRootView(
        WorkItem workItem,
        int childCount,
        Integer completedCount,
        Integer requiredCount,
        GroupStatus groupStatus) {
}
