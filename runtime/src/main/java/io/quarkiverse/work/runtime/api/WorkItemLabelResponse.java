package io.quarkiverse.work.runtime.api;

import io.quarkiverse.work.runtime.model.LabelPersistence;

public record WorkItemLabelResponse(
        String path,
        LabelPersistence persistence,
        String appliedBy) {
}
