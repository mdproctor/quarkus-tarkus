package io.quarkiverse.workitems.queues.service;

import java.util.Map;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import io.quarkiverse.workitems.queues.event.WorkItemQueueEvent;
import io.quarkiverse.workitems.runtime.event.WorkItemLifecycleEvent;
import io.quarkiverse.workitems.runtime.repository.WorkItemStore;

/**
 * CDI observer: bridges WorkItemLifecycleEvent to the filter evaluation engine.
 * This is the sole integration point between the core extension and the queues module.
 *
 * <p>
 * For each WorkItem mutation, this observer:
 * <ol>
 * <li>Retrieves the last-known queue membership from {@link QueueMembershipTracker}
 * (empty map for new items — they start with no prior queue history).</li>
 * <li>Creates a short-lived {@link QueueMembershipContext} holding that before-state.</li>
 * <li>Runs the filter engine — strips INFERRED labels, re-applies, persists.</li>
 * <li>Resolves the diff between before- and after-state via the context, firing
 * {@link WorkItemQueueEvent} (ADDED / REMOVED / CHANGED) as appropriate.</li>
 * <li>Updates the tracker with the new membership so the next event has a correct before.</li>
 * </ol>
 *
 * <p>
 * The {@link QueueMembershipContext} is a plain local variable — no CDI scope —
 * and becomes GC-eligible as soon as {@code resolve()} returns.
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

    @Transactional
    public void onLifecycleEvent(@Observes final WorkItemLifecycleEvent event) {
        workItemStore.get(event.workItemId()).ifPresent(wi -> {
            // 1. Before-state: last resolved membership (empty for new items)
            final Map<UUID, String> before = tracker.getBefore(wi.id);

            // 2. Temporary context — no CDI scope, GC-eligible after resolve() returns
            final QueueMembershipContext ctx = new QueueMembershipContext(wi.id, before);

            // 3. Evaluate: strips INFERRED labels, re-applies, persists
            filterEngine.evaluate(wi);

            // 4. Diff before vs after; fire ADDED / REMOVED / CHANGED; returns after-state
            final Map<UUID, String> after = ctx.resolve(wi, queueEventBus);

            // 5. Update tracker so the next event sees the correct before-state
            tracker.update(wi.id, after);

            // ctx is now unreferenced — GC-eligible
        });
    }
}
