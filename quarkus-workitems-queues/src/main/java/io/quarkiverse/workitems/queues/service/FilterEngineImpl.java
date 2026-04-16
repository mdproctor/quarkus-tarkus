package io.quarkiverse.workitems.queues.service;

import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.workitems.runtime.model.WorkItem;

@ApplicationScoped
public class FilterEngineImpl implements FilterEngine {

    @Override
    public void evaluate(final WorkItem workItem) {
        // Stub — full implementation in issue #61
    }

    @Override
    public void cascadeDelete(final UUID filterId) {
        // Stub — full implementation in issue #62
    }
}
