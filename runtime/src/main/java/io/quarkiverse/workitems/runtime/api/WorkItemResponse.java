package io.quarkiverse.workitems.runtime.api;

import java.time.Instant;
import java.util.UUID;

import io.quarkiverse.workitems.runtime.model.DelegationState;
import io.quarkiverse.workitems.runtime.model.WorkItemPriority;
import io.quarkiverse.workitems.runtime.model.WorkItemStatus;

public record WorkItemResponse(
        UUID id,
        String title,
        String description,
        String category,
        String formKey,
        WorkItemStatus status,
        WorkItemPriority priority,
        String assigneeId,
        String owner,
        String candidateGroups,
        String candidateUsers,
        String requiredCapabilities,
        String createdBy,
        DelegationState delegationState,
        String delegationChain,
        WorkItemStatus priorStatus,
        String payload,
        String resolution,
        Instant claimDeadline,
        Instant expiresAt,
        Instant followUpDate,
        Instant createdAt,
        Instant updatedAt,
        Instant assignedAt,
        Instant startedAt,
        Instant completedAt,
        Instant suspendedAt) {
}
