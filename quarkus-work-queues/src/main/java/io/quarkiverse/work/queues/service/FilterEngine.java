package io.casehub.work.queues.service;

import java.util.UUID;

import io.casehub.work.runtime.model.WorkItem;

public interface FilterEngine {
    void evaluate(WorkItem workItem);

    void cascadeDelete(UUID filterId);
}
