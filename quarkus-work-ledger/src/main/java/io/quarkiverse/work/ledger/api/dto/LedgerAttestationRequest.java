package io.quarkiverse.work.ledger.api.dto;

import io.quarkiverse.ledger.runtime.model.ActorType;
import io.quarkiverse.ledger.runtime.model.AttestationVerdict;

/**
 * Request body for posting a peer attestation on a ledger entry.
 *
 * @param attestorId identity of the actor providing the attestation
 * @param attestorType whether the attestor is a human, agent, or system
 * @param verdict the attestor's formal judgment on the ledger entry
 * @param evidence optional supporting evidence; {@code null} if not provided
 * @param confidence confidence level for this attestation, in the range 0.0–1.0
 */
public record LedgerAttestationRequest(
        String attestorId,
        ActorType attestorType,
        AttestationVerdict verdict,
        String evidence,
        double confidence) {
}
