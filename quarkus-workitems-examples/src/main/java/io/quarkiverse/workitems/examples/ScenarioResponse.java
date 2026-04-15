package io.quarkiverse.workitems.examples;

import java.util.List;
import java.util.UUID;

import io.quarkiverse.workitems.ledger.api.dto.LedgerEntryResponse;
import io.quarkiverse.workitems.runtime.api.AuditEntryResponse;

/**
 * Response returned by single-WorkItem scenario endpoints (Scenarios 1–3).
 *
 * @param scenario identifier of the scenario that was run
 * @param steps chronological log of each step taken
 * @param workItemId the UUID of the WorkItem driven through its lifecycle
 * @param ledgerEntries all ledger entries for the WorkItem, in sequence order
 * @param auditTrail all audit entries for the WorkItem, in occurrence order
 */
public record ScenarioResponse(
        String scenario,
        List<StepLog> steps,
        UUID workItemId,
        List<LedgerEntryResponse> ledgerEntries,
        List<AuditEntryResponse> auditTrail) {
}
