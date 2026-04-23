package io.quarkiverse.work.queues.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import io.quarkiverse.work.queues.event.QueueEventType;
import io.quarkiverse.work.queues.event.WorkItemQueueEvent;
import io.quarkiverse.work.queues.model.QueueView;
import io.quarkiverse.work.runtime.model.WorkItem;
import io.quarkiverse.work.runtime.service.LabelVocabularyService;

/**
 * Short-lived per-operation context that resolves queue lifecycle events by diffing
 * the last-known queue membership (from {@link QueueMembershipTracker}) against the
 * post-evaluate state.
 *
 * <h2>Why a tracker, not a live before-snapshot?</h2>
 * <p>
 * {@link io.quarkiverse.work.runtime.event.WorkItemLifecycleEvent} fires <em>after</em>
 * the WorkItem has already been mutated and persisted in the store. At observation time,
 * {@code workItemStore.get(id)} returns the post-mutation state — the pre-mutation state is gone.
 *
 * <p>
 * <strong>Concrete failure without the tracker:</strong>
 *
 * <pre>
 * // WorkItem is NEWLY CREATED with MANUAL label "legal/contracts" matching queue Q.
 * //
 * // WorkItemService.create() sequence:
 * //   1. persist workItem WITH the label already attached
 * //   2. fire WorkItemLifecycleEvent("CREATED")    ← label is already in the store
 * //
 * // FilterEvaluationObserver.onLifecycleEvent():
 * //   3. workItemStore.get(id) → returns item WITH label  ← post-mutation
 * //
 * // Without tracker — naive live snapshot:
 * //   before = computeMembership(liveEntity) = {Q}  ← label already present!
 * //   evaluate(wi) → label survives → after = {Q}
 * //   before == after → CHANGED fires              ← WRONG: should be ADDED
 * //
 * // With tracker:
 * //   before = tracker.getBefore(id) = {}           ← no prior history for new item
 * //   evaluate(wi) → label survives → after = {Q}
 * //   {} vs {Q} → ADDED fires                      ← correct
 * </pre>
 *
 * <p>
 * The same failure occurs for label removal: the label is deleted before the event fires,
 * so a live snapshot would also yield {} before and {} after, producing no event instead
 * of the correct {@code REMOVED}.
 *
 * <p>
 * The tracker bridges this by recording the after-state at the end of each event — which
 * is the correct before-state for the next event. It is DB-backed (V2001 migration) so
 * state survives JVM restarts.
 *
 * <h2>Event semantics</h2>
 * <ul>
 * <li>In before, not in after → {@link QueueEventType#REMOVED}</li>
 * <li>Not in before, in after → {@link QueueEventType#ADDED}</li>
 * <li>In both before and after → {@link QueueEventType#CHANGED} — item went through the
 * INFERRED-label strip + re-apply cycle and remained a queue member; signals that
 * the WorkItem's state changed while queue membership was preserved</li>
 * </ul>
 *
 * <h2>Testability</h2>
 * <p>
 * {@link #resolve(WorkItem, List, Consumer)} accepts the full {@link QueueView} list as a
 * parameter (rather than fetching it internally via Panache) and emits events via a
 * {@link Consumer}. This allows pure unit tests with no Quarkus runtime, no CDI, and no DB:
 *
 * <pre>{@code
 * List<WorkItemQueueEvent> fired = new ArrayList<>();
 * new QueueMembershipContext(workItemId, before).resolve(wi, queues, fired::add);
 * assertThat(fired).hasSize(1).first().extracting(...).isEqualTo(ADDED);
 * }</pre>
 *
 * In production, {@link FilterEvaluationObserver} passes {@code queueEventBus::fire}.
 *
 * <h2>GC lifecycle</h2>
 * <p>
 * Created as a local variable in {@link FilterEvaluationObserver}. Falls out of scope
 * immediately after {@code resolve()} returns — no CDI scope, no static reference holds it.
 */
final class QueueMembershipContext {

    private final UUID workItemId;

    /**
     * Last-known queue membership snapshot (queueViewId → queueName).
     * Loaded from {@link QueueMembershipTracker}; immutable once set.
     */
    private final Map<UUID, String> before;

    /**
     * Create a context with the given before-state.
     *
     * @param workItemId the WorkItem being tracked
     * @param before last-known queue membership from {@link QueueMembershipTracker};
     *        empty map for new items with no prior history
     */
    QueueMembershipContext(final UUID workItemId, final Map<UUID, String> before) {
        this.workItemId = workItemId;
        this.before = before;
    }

    /**
     * Compute the current queue membership from {@code wi}'s post-evaluate labels, diff
     * against the before-state, emit {@link WorkItemQueueEvent} instances for each change,
     * and return the after-state so the caller can update the tracker.
     *
     * <p>
     * The {@code allQueues} parameter is provided by the caller (rather than fetched
     * internally) to keep this class free of Panache dependencies, enabling pure unit testing.
     *
     * @param wi the WorkItem after {@code FilterEngine.evaluate()}; {@code wi.labels}
     *        reflects the post-evaluation label set
     * @param allQueues the complete list of {@link QueueView}s to evaluate membership against;
     *        typically {@code QueueView.listAll()} from the caller
     * @param emit event sink — receives one {@link WorkItemQueueEvent} per queue change;
     *        in production this is {@code queueEventBus::fire}; in tests a list collector
     * @return the post-evaluate queue membership (queueViewId → queueName) to store in the tracker
     */
    Map<UUID, String> resolve(
            final WorkItem wi,
            final List<QueueView> allQueues,
            final Consumer<WorkItemQueueEvent> emit) {

        final Set<String> labelPaths = wi.labels == null ? Set.of()
                : wi.labels.stream().map(l -> l.path).collect(Collectors.toSet());

        final Map<UUID, String> after = allQueues.stream()
                .filter(qv -> matchesAny(qv.labelPattern, labelPaths))
                .collect(Collectors.toMap(qv -> qv.id, qv -> qv.name));

        // REMOVED: in queue before, not after — item permanently left this queue
        before.forEach((id, name) -> {
            if (!after.containsKey(id)) {
                emit.accept(new WorkItemQueueEvent(workItemId, id, name, QueueEventType.REMOVED));
            }
        });

        // ADDED: not in queue before, is after — item newly entered this queue
        after.forEach((id, name) -> {
            if (!before.containsKey(id)) {
                emit.accept(new WorkItemQueueEvent(workItemId, id, name, QueueEventType.ADDED));
            }
        });

        // CHANGED: in both before and after — item survived the re-evaluation cycle;
        // INFERRED labels were stripped and re-applied, WorkItem state may have changed
        before.forEach((id, name) -> {
            if (after.containsKey(id)) {
                // Use the current queue name (after) in case it was renamed
                emit.accept(new WorkItemQueueEvent(
                        workItemId, id, after.get(id), QueueEventType.CHANGED));
            }
        });

        return after; // caller persists this in QueueMembershipTracker; context is GC-eligible
    }

    /**
     * Returns {@code true} if any label path in {@code labelPaths} matches {@code pattern}.
     * Delegates to {@link LabelVocabularyService#matchesPattern(String, String)}.
     */
    private static boolean matchesAny(final String pattern, final Set<String> labelPaths) {
        return labelPaths.stream()
                .anyMatch(path -> LabelVocabularyService.matchesPattern(pattern, path));
    }
}
