package io.quarkiverse.workitems.queues.service;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import io.quarkiverse.workitems.queues.model.WorkItemQueueMembership;

/**
 * Persistent store of last-known queue membership per WorkItem.
 *
 * <p>
 * Backed by the {@code work_item_queue_membership} table (see Flyway migration V2001).
 * This persistence means queue membership state survives JVM restarts — without it,
 * every restart would re-fire {@link io.quarkiverse.workitems.queues.event.QueueEventType#ADDED}
 * for every WorkItem currently in any queue, which is incorrect for consumers that track
 * cumulative state.
 *
 * <p>
 * Maintained by {@link FilterEvaluationObserver} on every WorkItem lifecycle event.
 * The stored map is used as the "before-state" when
 * {@link QueueMembershipContext#resolve(io.quarkiverse.workitems.runtime.model.WorkItem, java.util.List, java.util.function.Consumer)}
 * computes membership diffs.
 *
 * <h2>Concurrency</h2>
 * <p>
 * All operations run within the caller's transaction (they join via {@code Transactional.TxType.REQUIRED}).
 * Since lifecycle events are processed sequentially per WorkItem within a single request,
 * concurrent updates to the same WorkItem are not expected in normal operation.
 *
 * <h2>GC behaviour</h2>
 * <p>
 * When a WorkItem leaves all queues, its rows are deleted — no orphan rows accumulate.
 * Deleted WorkItems naturally lose their rows via cascading OR via the delete-all-then-insert
 * pattern in {@link #update}.
 */
@ApplicationScoped
class QueueMembershipTracker {

    /**
     * Return the last-known queue membership for the given WorkItem.
     *
     * <p>
     * Returns an empty map if the item has never been tracked (new item with no queue history).
     * Each map entry is {@code queueViewId → queueName}, matching the format expected by
     * {@link QueueMembershipContext}.
     *
     * @param workItemId the WorkItem UUID
     * @return immutable map of queueViewId → queueName; empty if not yet tracked
     */
    @Transactional
    Map<UUID, String> getBefore(final UUID workItemId) {
        return WorkItemQueueMembership.findByWorkItemId(workItemId).stream()
                .collect(Collectors.toMap(m -> m.queueViewId, m -> m.queueName));
    }

    /**
     * Replace the stored queue membership for the given WorkItem with {@code after}.
     *
     * <p>
     * Atomically deletes all existing rows for {@code workItemId} and inserts one row
     * per entry in {@code after}. An empty {@code after} map leaves no rows, allowing
     * the garbage collector to reclaim the WorkItem's entry from the membership table.
     *
     * <p>
     * Runs within the caller's transaction — if the transaction rolls back, neither
     * the WorkItem mutation nor the membership update is persisted.
     *
     * @param workItemId the WorkItem UUID
     * @param after the new queue membership (queueViewId → queueName); empty if item
     *        is no longer a member of any queue
     */
    @Transactional
    void update(final UUID workItemId, final Map<UUID, String> after) {
        WorkItemQueueMembership.deleteByWorkItemId(workItemId);
        after.forEach((queueViewId, queueName) -> {
            final WorkItemQueueMembership row = new WorkItemQueueMembership();
            row.workItemId = workItemId;
            row.queueViewId = queueViewId;
            row.queueName = queueName;
            row.persist();
        });
    }
}
