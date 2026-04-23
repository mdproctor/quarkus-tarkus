package io.quarkiverse.work.runtime.service;

import java.util.UUID;

import io.quarkiverse.work.runtime.model.WorkItemStatus;

public record WorkItemExpiredEvent(UUID workItemId, WorkItemStatus previousStatus) {
}
