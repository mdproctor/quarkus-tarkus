package io.casehub.work.runtime.api;

import io.casehub.work.runtime.model.LabelPersistence;

public record WorkItemLabelResponse(
        String path,
        LabelPersistence persistence,
        String appliedBy) {
}
