package io.quarkiverse.workitems.runtime.api;

import io.quarkiverse.workitems.runtime.model.LabelPersistence;

public record WorkItemLabelResponse(
        String path,
        LabelPersistence persistence,
        String appliedBy) {
}
