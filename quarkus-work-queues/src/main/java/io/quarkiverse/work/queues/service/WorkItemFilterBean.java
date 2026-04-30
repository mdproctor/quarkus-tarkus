package io.casehub.work.queues.service;

import java.util.List;

import io.casehub.work.queues.model.FilterAction;
import io.casehub.work.queues.model.FilterScope;
import io.casehub.work.runtime.model.WorkItem;

public interface WorkItemFilterBean {
    boolean matches(WorkItem workItem);

    List<FilterAction> actions();

    FilterScope scope();
}
