package io.quarkiverse.work.ledger.api.dto;

import java.time.Instant;

import io.quarkiverse.ledger.api.model.ActorType;

/**
 * REST response DTO carrying the computed trust score for a single actor.
 *
 * @param actorId the actor's identity string
 * @param actorType whether the actor is a HUMAN, AGENT, or SYSTEM
 * @param trustScore computed trust score in [0.0, 1.0]; neutral prior is 0.5
 * @param decisionCount total number of EVENT ledger entries attributed to this actor
 * @param overturnedCount number of decisions that received at least one negative attestation
 * @param attestationPositive total count of positive attestations (SOUND or ENDORSED)
 * @param attestationNegative total count of negative attestations (FLAGGED or CHALLENGED)
 * @param lastComputedAt when this score was last computed
 */
public record ActorTrustScoreResponse(
        String actorId,
        ActorType actorType,
        double trustScore,
        int decisionCount,
        int overturnedCount,
        int attestationPositive,
        int attestationNegative,
        Instant lastComputedAt) {
}
