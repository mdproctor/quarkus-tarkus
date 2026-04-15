package io.quarkiverse.tarkus.ledger.api;

import java.util.List;

import io.quarkiverse.ledger.runtime.model.LedgerAttestation;
import io.quarkiverse.tarkus.ledger.api.dto.LedgerAttestationResponse;
import io.quarkiverse.tarkus.ledger.api.dto.LedgerEntryResponse;
import io.quarkiverse.tarkus.ledger.model.WorkItemLedgerEntry;

/**
 * Static mapper between ledger JPA entities and REST response records.
 */
public final class LedgerMapper {

    private LedgerMapper() {
    }

    /**
     * Map a {@link LedgerAttestation} entity to its REST response representation.
     *
     * @param a the attestation entity; must not be {@code null}
     * @return the corresponding response record
     */
    public static LedgerAttestationResponse toResponse(final LedgerAttestation a) {
        return new LedgerAttestationResponse(
                a.id,
                a.ledgerEntryId,
                a.subjectId,
                a.attestorId,
                a.attestorType,
                a.attestorRole,
                a.verdict,
                a.evidence,
                a.confidence,
                a.occurredAt);
    }

    /**
     * Map a {@link WorkItemLedgerEntry} entity and its attestations to the REST response
     * representation.
     *
     * @param e the ledger entry entity; must not be {@code null}
     * @param attestations the list of attestations for this entry; may be empty but not {@code null}
     * @return the corresponding response record including mapped attestations
     */
    public static LedgerEntryResponse toResponse(final WorkItemLedgerEntry e,
            final List<LedgerAttestation> attestations) {
        return new LedgerEntryResponse(
                e.id,
                e.subjectId,
                e.sequenceNumber,
                e.entryType,
                e.commandType,
                e.eventType,
                e.actorId,
                e.actorType,
                e.actorRole,
                e.planRef,
                e.rationale,
                e.decisionContext,
                e.evidence,
                e.detail,
                e.causedByEntryId,
                e.correlationId,
                e.sourceEntityId,
                e.sourceEntityType,
                e.sourceEntitySystem,
                e.previousHash,
                e.digest,
                e.occurredAt,
                attestations.stream().map(LedgerMapper::toResponse).toList());
    }
}
