package io.quarkiverse.workitems.queues.service;

import java.util.UUID;

import io.quarkiverse.workitems.runtime.model.WorkItem;

public interface FilterEngine {
    void evaluate(WorkItem workItem);

    void cascadeDelete(UUID filterId);
}
