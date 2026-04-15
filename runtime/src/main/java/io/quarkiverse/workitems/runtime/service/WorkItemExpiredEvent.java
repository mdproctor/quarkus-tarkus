package io.quarkiverse.workitems.runtime.service;

import java.util.UUID;

import io.quarkiverse.workitems.runtime.model.WorkItemStatus;

public record WorkItemExpiredEvent(UUID workItemId, WorkItemStatus previousStatus) {
}
