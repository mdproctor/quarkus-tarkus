package io.casehub.work.runtime.service;

import java.util.UUID;

import io.casehub.work.runtime.model.WorkItemStatus;

public record WorkItemExpiredEvent(UUID workItemId, WorkItemStatus previousStatus) {
}
