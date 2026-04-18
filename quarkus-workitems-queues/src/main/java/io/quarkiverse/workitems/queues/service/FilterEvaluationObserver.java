package io.quarkiverse.workitems.queues.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import io.quarkiverse.workitems.queues.event.WorkItemQueueEvent;
import io.quarkiverse.workitems.queues.model.QueueView;
import io.quarkiverse.workitems.runtime.event.WorkItemLifecycleEvent;
import io.quarkiverse.workitems.runtime.repository.WorkItemStore;

/**
 * CDI observer: bridges {@link WorkItemLifecycleEvent} to the filter evaluation engine
 * and queue lifecycle event system.
 *
 * <p>
 * This is the sole integration point between the core WorkItems extension and the
 * queues module. For each WorkItem lifecycle event:
 *
 * <ol>
 * <li><strong>Before-state</strong> — retrieves the last-known queue membership from
 * {@link QueueMembershipTracker} (DB-backed; correct across JVM restarts).</li>
 * <li><strong>Queue snapshot</strong> — fetches all {@link QueueView}s once per event
 * so the same list is used for both the before→after diff and pattern matching.</li>
 * <li><strong>Create context</strong> — constructs a short-lived {@link QueueMembershipContext}
 * holding the before-state. No CDI scope; GC-eligible after {@code resolve()} returns.</li>
 * <li><strong>Evaluate</strong> — runs the filter engine: strips all INFERRED labels,
 * re-applies them via multi-pass evaluation, persists the updated WorkItem.</li>
 * <li><strong>Resolve</strong> — diffs before vs after, emits {@link WorkItemQueueEvent}
 * (ADDED / REMOVED / CHANGED) for each affected queue, returns the after-state.</li>
 * <li><strong>Update tracker</strong> — stores the after-state for the next event.</li>
 * </ol>
 *
 * <h2>Why the before-state comes from the tracker, not from the live entity</h2>
 * <p>
 * {@link WorkItemLifecycleEvent} fires <em>after</em> the WorkItem has already been
 * mutated in the store. Fetching the entity at observation time therefore yields the
 * post-mutation state — there is no "before" to compare against. The tracker bridges
 * this by persisting the resolved queue membership at the end of each event, making
 * it available as the "before" for the next event.
 *
 * <h2>CHANGED vs spurious REMOVED + ADDED</h2>
 * <p>
 * The filter engine strips all INFERRED labels before re-applying them. Without
 * coordination, a naive event emitter would fire REMOVED (during strip) then ADDED
 * (on re-apply) for items that never truly left the queue. The before/after diff in
 * {@link QueueMembershipContext} collapses this pair into a single CHANGED event.
 */
@ApplicationScoped
public class FilterEvaluationObserver {

    @Inject
    FilterEngine filterEngine;

    @Inject
    WorkItemStore workItemStore;

    @Inject
    Event<WorkItemQueueEvent> queueEventBus;

    @Inject
    QueueMembershipTracker tracker;

    /**
     * Observe a WorkItem lifecycle event, run filter evaluation, and fire queue events.
     *
     * @param event the lifecycle event fired by
     *        {@link io.quarkiverse.workitems.runtime.service.WorkItemService}
     */
    @Transactional
    public void onLifecycleEvent(@Observes final WorkItemLifecycleEvent event) {
        workItemStore.get(event.workItemId()).ifPresent(wi -> {
            // 1. Before-state: last resolved membership from the persistent tracker
            final Map<UUID, String> before = tracker.getBefore(wi.id);

            // 2. Fetch all QueueViews once — used by resolve() for pattern matching
            final List<QueueView> allQueues = QueueView.listAll();

            // 3. Short-lived context — no CDI scope; GC-eligible after resolve() returns
            final QueueMembershipContext ctx = new QueueMembershipContext(wi.id, before);

            // 4. Evaluate: strips INFERRED labels, re-applies via multi-pass, persists
            filterEngine.evaluate(wi);

            // 5. Diff before vs after; fire ADDED / REMOVED / CHANGED; returns after-state
            final Map<UUID, String> after = ctx.resolve(wi, allQueues, queueEventBus::fire);

            // 6. Update tracker so the next event sees the correct before-state
            tracker.update(wi.id, after);

            // ctx is now unreferenced — eligible for GC
        });
    }
}
