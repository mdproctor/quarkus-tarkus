package io.quarkiverse.work.queues.service;

import java.util.UUID;

import io.quarkiverse.work.runtime.model.WorkItem;

public interface FilterEngine {
    void evaluate(WorkItem workItem);

    void cascadeDelete(UUID filterId);
}
