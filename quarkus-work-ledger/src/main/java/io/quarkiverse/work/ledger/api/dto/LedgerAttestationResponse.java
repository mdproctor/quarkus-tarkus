package io.quarkiverse.work.ledger.api.dto;

import java.time.Instant;
import java.util.UUID;

import io.quarkiverse.ledger.runtime.model.ActorType;
import io.quarkiverse.ledger.runtime.model.AttestationVerdict;

/**
 * Response body representing a single peer attestation on a ledger entry.
 *
 * @param id the attestation UUID
 * @param ledgerEntryId the ledger entry this attestation targets
 * @param subjectId the aggregate identifier this attestation belongs to (denormalized)
 * @param attestorId identity of the attestor
 * @param attestorType whether the attestor is a human, agent, or system
 * @param attestorRole functional role of the attestor; may be {@code null}
 * @param verdict the attestor's formal judgment
 * @param evidence supporting evidence; may be {@code null}
 * @param confidence confidence level in the range 0.0–1.0
 * @param occurredAt when the attestation was recorded
 */
public record LedgerAttestationResponse(
        UUID id,
        UUID ledgerEntryId,
        UUID subjectId,
        String attestorId,
        ActorType attestorType,
        String attestorRole,
        AttestationVerdict verdict,
        String evidence,
        double confidence,
        Instant occurredAt) {
}
