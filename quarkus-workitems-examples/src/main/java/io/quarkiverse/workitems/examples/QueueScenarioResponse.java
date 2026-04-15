package io.quarkiverse.workitems.examples;

import java.util.List;
import java.util.UUID;

import io.quarkiverse.workitems.ledger.api.dto.ActorTrustScoreResponse;
import io.quarkiverse.workitems.ledger.api.dto.LedgerEntryResponse;
import io.quarkiverse.workitems.runtime.api.AuditEntryResponse;

/**
 * Response returned by the Document Queue scenario (Scenario 4).
 *
 * @param scenario identifier of the scenario
 * @param steps chronological log of each step
 * @param workItemIds UUIDs of the WorkItems created in the queue
 * @param allLedgerEntries all ledger entries across all WorkItems
 * @param allAuditEntries all audit entries across all WorkItems
 * @param reviewerBobTrust computed trust score for reviewer-bob
 * @param reviewerAliceTrust computed trust score for reviewer-alice
 */
public record QueueScenarioResponse(
        String scenario,
        List<StepLog> steps,
        List<UUID> workItemIds,
        List<LedgerEntryResponse> allLedgerEntries,
        List<AuditEntryResponse> allAuditEntries,
        ActorTrustScoreResponse reviewerBobTrust,
        ActorTrustScoreResponse reviewerAliceTrust) {
}
