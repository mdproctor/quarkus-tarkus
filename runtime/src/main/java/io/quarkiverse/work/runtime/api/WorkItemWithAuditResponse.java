package io.quarkiverse.work.runtime.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.quarkiverse.work.runtime.model.DelegationState;
import io.quarkiverse.work.runtime.model.WorkItemPriority;
import io.quarkiverse.work.runtime.model.WorkItemStatus;

public record WorkItemWithAuditResponse(
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
        Instant suspendedAt,
        List<WorkItemLabelResponse> labels,
        List<AuditEntryResponse> auditTrail,
        /**
         * Confidence score from the AI agent that created this WorkItem (0.0–1.0).
         * Null when created by a human or when no confidence metadata was provided.
         */
        Double confidenceScore,
        /**
         * Opaque caller-supplied routing key set at spawn time.
         * Null for WorkItems not created via spawn.
         */
        String callerRef,
        Long version) {
}
