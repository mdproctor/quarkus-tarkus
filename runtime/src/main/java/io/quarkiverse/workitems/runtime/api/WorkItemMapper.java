package io.quarkiverse.workitems.runtime.api;

import java.util.List;

import io.quarkiverse.workitems.runtime.model.AuditEntry;
import io.quarkiverse.workitems.runtime.model.WorkItem;
import io.quarkiverse.workitems.runtime.model.WorkItemCreateRequest;

public final class WorkItemMapper {

    private WorkItemMapper() {
    }

    public static WorkItemResponse toResponse(final WorkItem wi) {
        return new WorkItemResponse(
                wi.id, wi.title, wi.description, wi.category, wi.formKey,
                wi.status, wi.priority, wi.assigneeId, wi.owner,
                wi.candidateGroups, wi.candidateUsers, wi.requiredCapabilities,
                wi.createdBy, wi.delegationState, wi.delegationChain,
                wi.priorStatus, wi.payload, wi.resolution,
                wi.claimDeadline, wi.expiresAt, wi.followUpDate,
                wi.createdAt, wi.updatedAt, wi.assignedAt, wi.startedAt,
                wi.completedAt, wi.suspendedAt);
    }

    public static AuditEntryResponse toAuditResponse(final AuditEntry e) {
        return new AuditEntryResponse(e.id, e.event, e.actor, e.detail, e.occurredAt);
    }

    public static WorkItemWithAuditResponse toWithAudit(final WorkItem wi, final List<AuditEntry> trail) {
        final List<AuditEntryResponse> auditResponses = trail.stream()
                .map(WorkItemMapper::toAuditResponse)
                .toList();
        return new WorkItemWithAuditResponse(
                wi.id, wi.title, wi.description, wi.category, wi.formKey,
                wi.status, wi.priority, wi.assigneeId, wi.owner,
                wi.candidateGroups, wi.candidateUsers, wi.requiredCapabilities,
                wi.createdBy, wi.delegationState, wi.delegationChain,
                wi.priorStatus, wi.payload, wi.resolution,
                wi.claimDeadline, wi.expiresAt, wi.followUpDate,
                wi.createdAt, wi.updatedAt, wi.assignedAt, wi.startedAt,
                wi.completedAt, wi.suspendedAt,
                auditResponses);
    }

    public static WorkItemCreateRequest toServiceRequest(final CreateWorkItemRequest req) {
        return new WorkItemCreateRequest(
                req.title(), req.description(), req.category(), req.formKey(),
                req.priority(), req.assigneeId(), req.candidateGroups(),
                req.candidateUsers(), req.requiredCapabilities(), req.createdBy(),
                req.payload(), req.claimDeadline(), req.expiresAt(), req.followUpDate());
    }
}
