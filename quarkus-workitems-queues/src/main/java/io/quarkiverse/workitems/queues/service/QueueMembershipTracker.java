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
 * Backed by the {@code work_item_queue_membership} DB table (Flyway migration V2001).
 * {@link FilterEvaluationObserver} writes to this tracker at the end of every
 * {@link io.quarkiverse.workitems.runtime.event.WorkItemLifecycleEvent}, recording the
 * resolved membership as the authoritative "before-state" for the next event.
 *
 * <h2>Why DB, not an in-memory map?</h2>
 * <p>
 * An in-memory {@code ConcurrentHashMap} would work correctly within a single JVM session
 * but fails on restart. Consider:
 *
 * <pre>
 * JVM instance 1:
 *   Item A enters queue Q → ADDED fires → tracker records {Q}
 *   JVM RESTARTS
 *
 * JVM instance 2 (in-memory tracker is empty):
 *   Item A status changes → lifecycle event fires
 *   tracker.getBefore(A) = {}  ← empty (tracker was reset)
 *   evaluate(A) → still has label → after = {Q}
 *   {} vs {Q} → ADDED fires again  ← WRONG: item never left the queue
 * </pre>
 *
 * <p>
 * With DB-backed persistence:
 *
 * <pre>
 * JVM instance 2 (tracker reads from DB):
 *   tracker.getBefore(A) = {Q}  ← correct: loaded from work_item_queue_membership table
 *   evaluate(A) → label survives → after = {Q}
 *   {Q} vs {Q} → CHANGED fires  ← correct
 * </pre>
 *
 * <h2>Why the tracker exists at all (the root cause)</h2>
 * <p>
 * {@link io.quarkiverse.workitems.runtime.event.WorkItemLifecycleEvent} fires AFTER the
 * WorkItem mutation is persisted. When the observer fetches the entity, the pre-mutation
 * state is gone. The tracker bridges this timing gap by recording what the queue membership
 * WAS (at the end of the previous event) so it can serve as "before" for the next event.
 * See {@link QueueMembershipContext} for the full counter-example.
 *
 * <h2>Concurrency</h2>
 * <p>
 * All operations run within the caller's transaction. Since lifecycle events are processed
 * sequentially per WorkItem within a single request, concurrent updates to the same
 * WorkItem are not expected in normal operation.
 *
 * <h2>GC behaviour</h2>
 * <p>
 * When a WorkItem leaves all queues, its rows are deleted — no orphan rows accumulate.
 * The per-event context object ({@link QueueMembershipContext}) is a local variable that
 * falls out of scope immediately after {@code resolve()} returns.
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
