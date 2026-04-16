package io.quarkiverse.workitems.queues.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import io.quarkiverse.workitems.runtime.event.WorkItemLifecycleEvent;
import io.quarkiverse.workitems.runtime.repository.WorkItemRepository;

/**
 * CDI observer: bridges WorkItemLifecycleEvent to the filter evaluation engine.
 * This is the sole integration point between the core extension and the queues module.
 */
@ApplicationScoped
public class FilterEvaluationObserver {

    @Inject
    FilterEngine filterEngine;

    @Inject
    WorkItemRepository workItemRepo;

    @Transactional
    public void onLifecycleEvent(@Observes final WorkItemLifecycleEvent event) {
        workItemRepo.findById(event.workItemId()).ifPresent(filterEngine::evaluate);
    }
}
