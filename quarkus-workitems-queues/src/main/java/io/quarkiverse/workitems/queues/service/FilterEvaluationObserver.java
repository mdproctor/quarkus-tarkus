package io.quarkiverse.workitems.queues.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import io.quarkiverse.workitems.runtime.event.WorkItemLifecycleEvent;
import io.quarkiverse.workitems.runtime.repository.WorkItemStore;

/**
 * CDI observer: bridges WorkItemLifecycleEvent to the filter evaluation engine.
 * This is the sole integration point between the core extension and the queues module.
 */
@ApplicationScoped
public class FilterEvaluationObserver {

    @Inject
    FilterEngine filterEngine;

    @Inject
    WorkItemStore workItemStore;

    @Transactional
    public void onLifecycleEvent(@Observes final WorkItemLifecycleEvent event) {
        workItemStore.get(event.workItemId()).ifPresent(filterEngine::evaluate);
    }
}
