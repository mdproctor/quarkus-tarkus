package io.quarkiverse.workitems.examples.semantic;

import java.util.List;
import java.util.UUID;

import io.quarkiverse.workitems.examples.StepLog;
import io.quarkiverse.workitems.runtime.api.AuditEntryResponse;

/**
 * Response returned by the NDA review semantic routing scenario.
 *
 * @param scenario identifier of the scenario
 * @param steps chronological log of each step taken
 * @param workItemId UUID of the WorkItem driven through its lifecycle
 * @param assignedTo workerId selected by the semantic routing strategy
 * @param resolvedBy workerId who completed the WorkItem
 * @param auditTrail all audit entries for the WorkItem
 */
public record SemanticRoutingResponse(
        String scenario,
        List<StepLog> steps,
        UUID workItemId,
        String assignedTo,
        String resolvedBy,
        List<AuditEntryResponse> auditTrail) {
}
