package io.casehub.work.examples.filterrules;

import java.util.List;
import java.util.UUID;

import io.casehub.work.examples.StepLog;
import io.casehub.work.runtime.api.AuditEntryResponse;

/**
 * Response returned by the filter rules dynamic labelling scenario.
 *
 * @param scenario identifier of the scenario
 * @param steps chronological log of each step taken
 * @param highPriorityWorkItemId UUID of the HIGH-priority WorkItem
 * @param normalPriorityWorkItemId UUID of the MEDIUM-priority WorkItem
 * @param urgentLabelOnHighPriority true if the "urgent" label was applied to the HIGH-priority item
 * @param noUrgentLabelOnNormalPriority true if the "urgent" label was NOT applied to the MEDIUM-priority item
 * @param auditTrail all audit entries for both WorkItems
 */
public record FilterRulesResponse(
        String scenario,
        List<StepLog> steps,
        UUID highPriorityWorkItemId,
        UUID normalPriorityWorkItemId,
        boolean urgentLabelOnHighPriority,
        boolean noUrgentLabelOnNormalPriority,
        List<AuditEntryResponse> auditTrail) {
}
