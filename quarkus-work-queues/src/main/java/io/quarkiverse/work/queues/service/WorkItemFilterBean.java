package io.quarkiverse.work.queues.service;

import java.util.List;

import io.quarkiverse.work.queues.model.FilterAction;
import io.quarkiverse.work.queues.model.FilterScope;
import io.quarkiverse.work.runtime.model.WorkItem;

public interface WorkItemFilterBean {
    boolean matches(WorkItem workItem);

    List<FilterAction> actions();

    FilterScope scope();
}
