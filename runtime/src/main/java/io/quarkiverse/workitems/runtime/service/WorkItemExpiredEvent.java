package io.quarkiverse.tarkus.runtime.service;

import java.util.UUID;

import io.quarkiverse.tarkus.runtime.model.WorkItemStatus;

public record WorkItemExpiredEvent(UUID workItemId, WorkItemStatus previousStatus) {
}
