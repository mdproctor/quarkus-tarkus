package io.quarkiverse.workitems.queues.service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * In-memory store of last-known queue membership per WorkItem.
 *
 * <p>
 * Maintained by {@link FilterEvaluationObserver} on every lifecycle event.
 * The stored map is used as the "before" state when computing queue membership diffs,
 * ensuring that events reflect changes between the previous known state and the current
 * state — not between the post-mutation store state and itself.
 *
 * <p>
 * Lifecycle notes:
 * <ul>
 * <li>New items start with an empty before-state (no prior queue membership).</li>
 * <li>Items that leave all queues have their entry removed, allowing GC.</li>
 * <li>The map is reset on JVM restart; this causes ADDED events to re-fire on the
 * first mutation after a restart, which is acceptable refresh behaviour.</li>
 * </ul>
 *
 * <p>
 * Stored as {@code Map<queueViewId, queueName>} — plain strings, no JPA entity references.
 */
@ApplicationScoped
class QueueMembershipTracker {

    private final ConcurrentHashMap<UUID, Map<UUID, String>> lastKnown = new ConcurrentHashMap<>();

    /**
     * Returns the last-known queue membership for the given WorkItem.
     * Returns an empty map if the item has never been tracked (new item).
     *
     * @param workItemId the WorkItem UUID
     * @return immutable map of queueViewId → queueName; empty if not yet tracked
     */
    Map<UUID, String> getBefore(final UUID workItemId) {
        return lastKnown.getOrDefault(workItemId, Map.of());
    }

    /**
     * Record the current queue membership for the given WorkItem.
     * Removes the entry entirely if {@code after} is empty, allowing GC.
     *
     * @param workItemId the WorkItem UUID
     * @param after the new queue membership (queueViewId → queueName); may be empty
     */
    void update(final UUID workItemId, final Map<UUID, String> after) {
        if (after.isEmpty()) {
            lastKnown.remove(workItemId);
        } else {
            lastKnown.put(workItemId, Map.copyOf(after));
        }
    }
}
