package io.quarkiverse.work.ledger.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.quarkiverse.ledger.runtime.model.ActorType;
import io.quarkiverse.ledger.runtime.model.LedgerEntryType;

/**
 * Response body representing a single ledger entry with its attestations.
 *
 * @param id the ledger entry UUID
 * @param subjectId the aggregate identifier this entry belongs to (the WorkItem UUID)
 * @param sequenceNumber position in the per-subject ledger sequence (1-based)
 * @param entryType whether this is a command, event, or attestation record
 * @param commandType the actor's expressed intent; may be {@code null}
 * @param eventType the observable fact after execution; may be {@code null}
 * @param actorId identity of the actor
 * @param actorType whether the actor is human, agent, or system
 * @param actorRole functional role of the actor; may be {@code null}
 * @param planRef policy/procedure reference governing this action; may be {@code null}
 * @param rationale the actor's stated basis for the decision; may be {@code null}
 * @param decisionContext JSON snapshot of state at transition time; may be {@code null}
 * @param evidence structured evidence; may be {@code null}
 * @param detail free-text or JSON transition detail; may be {@code null}
 * @param causedByEntryId FK to the entry that caused this one; may be {@code null}
 * @param correlationId OpenTelemetry trace ID; may be {@code null}
 * @param sourceEntityId external entity identifier; may be {@code null}
 * @param sourceEntityType type of the external entity; may be {@code null}
 * @param sourceEntitySystem system owning the external entity; may be {@code null}
 * @param previousHash SHA-256 digest of the preceding entry; may be {@code null}
 * @param digest SHA-256 digest of this entry's canonical content; may be {@code null}
 * @param occurredAt when this entry was recorded
 * @param attestations peer attestations on this entry
 */
public record LedgerEntryResponse(
        UUID id,
        UUID subjectId,
        int sequenceNumber,
        LedgerEntryType entryType,
        String commandType,
        String eventType,
        String actorId,
        ActorType actorType,
        String actorRole,
        String planRef,
        String rationale,
        String decisionContext,
        String evidence,
        String detail,
        UUID causedByEntryId,
        String correlationId,
        String sourceEntityId,
        String sourceEntityType,
        String sourceEntitySystem,
        String previousHash,
        String digest,
        Instant occurredAt,
        List<LedgerAttestationResponse> attestations) {
}
