package io.quarkiverse.workitems.queues.service;

import io.quarkiverse.workitems.runtime.model.WorkItem;

public interface FilterConditionEvaluator {
    String language();

    boolean evaluate(WorkItem workItem, String expression);
}
