package io.quarkiverse.workitems.queues.service;

import java.util.List;

import io.quarkiverse.workitems.queues.model.FilterAction;
import io.quarkiverse.workitems.queues.model.FilterScope;
import io.quarkiverse.workitems.runtime.model.WorkItem;

public interface WorkItemFilterBean {
    boolean matches(WorkItem workItem);

    List<FilterAction> actions();

    FilterScope scope();
}
