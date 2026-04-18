package io.quarkiverse.workitems.queues.service;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.enterprise.event.Event;

import io.quarkiverse.workitems.queues.event.QueueEventType;
import io.quarkiverse.workitems.queues.event.WorkItemQueueEvent;
import io.quarkiverse.workitems.queues.model.QueueView;
import io.quarkiverse.workitems.runtime.model.WorkItem;
import io.quarkiverse.workitems.runtime.service.LabelVocabularyService;

/**
 * Short-lived per-operation context that resolves queue lifecycle events by diffing
 * the last-known queue membership (from {@link QueueMembershipTracker}) against the
 * post-evaluate state.
 *
 * <h2>Why a tracker, not a live before-snapshot?</h2>
 * <p>
 * {@link io.quarkiverse.workitems.runtime.event.WorkItemLifecycleEvent} fires <em>after</em>
 * the WorkItem has already been mutated in the store. Fetching the WorkItem at event observation
 * time therefore yields the post-mutation state, making it impossible to construct a
 * meaningful "before" from the live entity. The tracker solves this by persisting the
 * last-resolved queue membership between events.
 *
 * <h2>Lifecycle</h2>
 * <ol>
 * <li>Created as a local variable in {@link FilterEvaluationObserver}.</li>
 * <li>{@link #resolve} is called immediately after {@code FilterEngine.evaluate()} returns.</li>
 * <li>Returns the after-state so the caller can update the tracker.</li>
 * <li>Falls out of scope and becomes GC-eligible — no static or CDI references hold it.</li>
 * </ol>
 *
 * <h2>Event semantics</h2>
 * <ul>
 * <li>In before, not in after → {@link QueueEventType#REMOVED}</li>
 * <li>Not in before, in after → {@link QueueEventType#ADDED}</li>
 * <li>In both before and after → {@link QueueEventType#CHANGED} (item went through
 * the INFERRED-label strip + re-apply cycle but remained a queue member)</li>
 * </ul>
 */
final class QueueMembershipContext {

    private final UUID workItemId;
    /** Last-known membership snapshot (queueViewId → queueName). Immutable; safe to read concurrently. */
    private final Map<UUID, String> before;

    QueueMembershipContext(final UUID workItemId, final Map<UUID, String> before) {
        this.workItemId = workItemId;
        this.before = before;
    }

    /**
     * Compute the current queue membership from {@code wi}'s post-evaluate labels, diff
     * against the before-state, fire appropriate {@link WorkItemQueueEvent} instances, and
     * return the after-map so the caller can update the tracker.
     *
     * @param wi the WorkItem after {@code FilterEngine.evaluate()}; {@code wi.labels} is current
     * @param bus the CDI event bus for firing queue events
     * @return the post-evaluate queue membership (queueViewId → queueName), to be stored in the tracker
     */
    Map<UUID, String> resolve(final WorkItem wi, final Event<WorkItemQueueEvent> bus) {
        final Set<String> labelPaths = wi.labels == null ? Set.of()
                : wi.labels.stream().map(l -> l.path).collect(Collectors.toSet());

        final Map<UUID, String> after = QueueView.<QueueView> listAll().stream()
                .filter(qv -> matchesAny(qv.labelPattern, labelPaths))
                .collect(Collectors.toMap(qv -> qv.id, qv -> qv.name));

        // REMOVED: was in queue before, not after
        before.forEach((id, name) -> {
            if (!after.containsKey(id)) {
                bus.fire(new WorkItemQueueEvent(workItemId, id, name, QueueEventType.REMOVED));
            }
        });

        // ADDED: not in queue before, is after
        after.forEach((id, name) -> {
            if (!before.containsKey(id)) {
                bus.fire(new WorkItemQueueEvent(workItemId, id, name, QueueEventType.ADDED));
            }
        });

        // CHANGED: in both — item went through re-evaluation cycle and remains in queue
        before.forEach((id, name) -> {
            if (after.containsKey(id)) {
                bus.fire(new WorkItemQueueEvent(workItemId, id, name, QueueEventType.CHANGED));
            }
        });

        return after; // caller stores this in QueueMembershipTracker; context is now GC-eligible
    }

    private static boolean matchesAny(final String pattern, final Set<String> labelPaths) {
        return labelPaths.stream().anyMatch(path -> LabelVocabularyService.matchesPattern(pattern, path));
    }
}
