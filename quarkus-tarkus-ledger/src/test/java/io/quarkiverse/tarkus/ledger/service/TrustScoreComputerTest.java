package io.quarkiverse.tarkus.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.quarkiverse.tarkus.ledger.model.ActorType;
import io.quarkiverse.tarkus.ledger.model.AttestationVerdict;
import io.quarkiverse.tarkus.ledger.model.LedgerAttestation;
import io.quarkiverse.tarkus.ledger.model.LedgerEntry;
import io.quarkiverse.tarkus.ledger.model.LedgerEntryType;

/**
 * Pure unit tests for {@link TrustScoreComputer} — no Quarkus context required.
 *
 * <p>
 * EigenTrust-inspired score algorithm: each decision's score is derived from its
 * attestation verdict majority; recent decisions are weighted more heavily via
 * exponential decay (half-life = {@code decayHalfLifeDays}). The final trust score
 * is the weighted average across all decisions, clamped to [0.0, 1.0].
 */
class TrustScoreComputerTest {

    /** Half-life of 90 days — matches the default in {@link LedgerConfig.TrustScoreConfig}. */
    private final TrustScoreComputer computer = new TrustScoreComputer(90);

    private final Instant now = Instant.now();

    // -------------------------------------------------------------------------
    // Fixture helpers
    // -------------------------------------------------------------------------

    /**
     * Creates a minimal {@link LedgerEntry} acting as a decision for the given actor.
     *
     * @param actorId the actor identifier
     * @param occurredAt when the decision took place
     * @return a populated but unpersisted entry
     */
    private LedgerEntry decision(final String actorId, final Instant occurredAt) {
        final LedgerEntry e = new LedgerEntry();
        e.id = UUID.randomUUID();
        e.workItemId = UUID.randomUUID();
        e.sequenceNumber = 1;
        e.entryType = LedgerEntryType.EVENT;
        e.actorId = actorId;
        e.actorType = ActorType.HUMAN;
        e.occurredAt = occurredAt;
        return e;
    }

    /**
     * Creates an {@link LedgerAttestation} with the given verdict for the given entry.
     *
     * @param entryId the ledger entry being attested
     * @param verdict the attestor's verdict
     * @return a populated but unpersisted attestation
     */
    private LedgerAttestation attestation(final UUID entryId, final AttestationVerdict verdict) {
        final LedgerAttestation a = new LedgerAttestation();
        a.id = UUID.randomUUID();
        a.ledgerEntryId = entryId;
        a.attestorId = "peer";
        a.attestorType = ActorType.HUMAN;
        a.verdict = verdict;
        a.confidence = 0.9;
        return a;
    }

    // -------------------------------------------------------------------------
    // Empty history
    // -------------------------------------------------------------------------

    @Test
    void emptyHistory_returnsNeutralScore() {
        final TrustScoreComputer.ActorScore score = computer.compute(List.of(), Map.of(), now);

        assertThat(score.trustScore()).isCloseTo(0.5, within(0.01));
        assertThat(score.decisionCount()).isEqualTo(0);
    }

    // -------------------------------------------------------------------------
    // Single decision — no attestations
    // -------------------------------------------------------------------------

    @Test
    void singleCleanDecision_noAttestations_returnsHighScore() {
        final LedgerEntry d = decision("alice", now.minus(1, ChronoUnit.HOURS));

        final TrustScoreComputer.ActorScore score = computer.compute(
                List.of(d), Map.of(), now);

        assertThat(score.trustScore()).isCloseTo(1.0, within(0.01));
        assertThat(score.decisionCount()).isEqualTo(1);
    }

    // -------------------------------------------------------------------------
    // Single decision — positive attestation
    // -------------------------------------------------------------------------

    @Test
    void singleDecisionWithSoundAttestation_returnsHighScore() {
        final LedgerEntry d = decision("alice", now.minus(1, ChronoUnit.HOURS));
        final LedgerAttestation a = attestation(d.id, AttestationVerdict.SOUND);

        final TrustScoreComputer.ActorScore score = computer.compute(
                List.of(d), Map.of(d.id, List.of(a)), now);

        assertThat(score.trustScore()).isCloseTo(1.0, within(0.01));
        assertThat(score.attestationPositive()).isEqualTo(1);
    }

    // -------------------------------------------------------------------------
    // Single decision — negative attestation
    // -------------------------------------------------------------------------

    @Test
    void singleDecisionWithFlaggedAttestation_returnsLowScore() {
        final LedgerEntry d = decision("alice", now.minus(1, ChronoUnit.HOURS));
        final LedgerAttestation a = attestation(d.id, AttestationVerdict.FLAGGED);

        final TrustScoreComputer.ActorScore score = computer.compute(
                List.of(d), Map.of(d.id, List.of(a)), now);

        assertThat(score.trustScore()).isCloseTo(0.0, within(0.01));
        assertThat(score.overturnedCount()).isEqualTo(1);
        assertThat(score.attestationNegative()).isEqualTo(1);
    }

    // -------------------------------------------------------------------------
    // Mixed attestations — majority negative
    // -------------------------------------------------------------------------

    @Test
    void mixedAttestations_majority_negative_returnsLowScore() {
        final LedgerEntry d = decision("alice", now.minus(1, ChronoUnit.HOURS));
        final LedgerAttestation sound = attestation(d.id, AttestationVerdict.SOUND);
        final LedgerAttestation flagged1 = attestation(d.id, AttestationVerdict.FLAGGED);
        final LedgerAttestation flagged2 = attestation(d.id, AttestationVerdict.FLAGGED);

        final TrustScoreComputer.ActorScore score = computer.compute(
                List.of(d), Map.of(d.id, List.of(sound, flagged1, flagged2)), now);

        // Majority negative → decision score = 0.0 → weighted average = 0.0
        assertThat(score.trustScore()).isCloseTo(0.0, within(0.01));
    }

    // -------------------------------------------------------------------------
    // Mixed attestations — majority positive
    // -------------------------------------------------------------------------

    @Test
    void mixedAttestations_majority_positive_returnsMidScore() {
        final LedgerEntry d = decision("alice", now.minus(1, ChronoUnit.HOURS));
        final LedgerAttestation sound1 = attestation(d.id, AttestationVerdict.SOUND);
        final LedgerAttestation sound2 = attestation(d.id, AttestationVerdict.SOUND);
        final LedgerAttestation flagged = attestation(d.id, AttestationVerdict.FLAGGED);

        final TrustScoreComputer.ActorScore score = computer.compute(
                List.of(d), Map.of(d.id, List.of(sound1, sound2, flagged)), now);

        // Majority positive with one dissenter → partial score
        assertThat(score.trustScore()).isGreaterThan(0.0);
        assertThat(score.trustScore()).isLessThanOrEqualTo(1.0);
    }

    // -------------------------------------------------------------------------
    // Multiple decisions — all clean
    // -------------------------------------------------------------------------

    @Test
    void multipleDecisions_allClean_returnsHighScore() {
        final LedgerEntry d1 = decision("alice", now.minus(1, ChronoUnit.DAYS));
        final LedgerEntry d2 = decision("alice", now.minus(2, ChronoUnit.DAYS));
        final LedgerEntry d3 = decision("alice", now.minus(3, ChronoUnit.DAYS));

        final TrustScoreComputer.ActorScore score = computer.compute(
                List.of(d1, d2, d3), Map.of(), now);

        assertThat(score.trustScore()).isCloseTo(1.0, within(0.01));
        assertThat(score.decisionCount()).isEqualTo(3);
    }

    // -------------------------------------------------------------------------
    // Multiple decisions — mixed
    // -------------------------------------------------------------------------

    @Test
    void multipleDecisions_mixed_returnsProportionalScore() {
        // 2 clean decisions + 1 flagged decision
        final LedgerEntry clean1 = decision("alice", now.minus(1, ChronoUnit.DAYS));
        final LedgerEntry clean2 = decision("alice", now.minus(2, ChronoUnit.DAYS));
        final LedgerEntry bad = decision("alice", now.minus(3, ChronoUnit.DAYS));
        final LedgerAttestation flagged = attestation(bad.id, AttestationVerdict.FLAGGED);

        final TrustScoreComputer.ActorScore score = computer.compute(
                List.of(clean1, clean2, bad),
                Map.of(bad.id, List.of(flagged)),
                now);

        assertThat(score.trustScore()).isGreaterThan(0.0);
        assertThat(score.trustScore()).isLessThan(1.0);
        assertThat(score.decisionCount()).isEqualTo(3);
    }

    // -------------------------------------------------------------------------
    // Recency weighting
    // -------------------------------------------------------------------------

    @Test
    void recencyWeighting_recentDecisionWeightedMore() {
        // Recent decision (1 day ago) has SOUND attestation (positive)
        final LedgerEntry recent = decision("alice", now.minus(1, ChronoUnit.DAYS));
        final LedgerAttestation recentSound = attestation(recent.id, AttestationVerdict.SOUND);

        // Old decision (180 days ago) has FLAGGED attestation (negative)
        final LedgerEntry old = decision("alice", now.minus(180, ChronoUnit.DAYS));
        final LedgerAttestation oldFlagged = attestation(old.id, AttestationVerdict.FLAGGED);

        final TrustScoreComputer.ActorScore score = computer.compute(
                List.of(recent, old),
                Map.of(recent.id, List.of(recentSound), old.id, List.of(oldFlagged)),
                now);

        // Recent positive decision outweighs old negative one → score > 0.5
        assertThat(score.trustScore()).isGreaterThan(0.5);
    }

    // -------------------------------------------------------------------------
    // Half-life respected
    // -------------------------------------------------------------------------

    @Test
    void halfLifeRespected_oldDecisionHasLessWeight() {
        // Use a short half-life (30 days) to make the effect obvious
        final TrustScoreComputer shortHalfLife = new TrustScoreComputer(30);

        // Recent decision: SOUND
        final LedgerEntry recent = decision("alice", now.minus(1, ChronoUnit.DAYS));
        final LedgerAttestation recentSound = attestation(recent.id, AttestationVerdict.SOUND);

        // Very old decision (365 days ago): FLAGGED — should carry almost no weight
        final LedgerEntry veryOld = decision("alice", now.minus(365, ChronoUnit.DAYS));
        final LedgerAttestation oldFlagged = attestation(veryOld.id, AttestationVerdict.FLAGGED);

        final TrustScoreComputer.ActorScore score = shortHalfLife.compute(
                List.of(recent, veryOld),
                Map.of(recent.id, List.of(recentSound), veryOld.id, List.of(oldFlagged)),
                now);

        // Old decision decays to near zero → recent positive dominates → score > 0.5
        assertThat(score.trustScore()).isGreaterThan(0.5);
    }

    // -------------------------------------------------------------------------
    // ENDORSED counts as positive
    // -------------------------------------------------------------------------

    @Test
    void endorsedCountsAsPositive() {
        final LedgerEntry d = decision("alice", now.minus(1, ChronoUnit.HOURS));
        final LedgerAttestation endorsed = attestation(d.id, AttestationVerdict.ENDORSED);

        final TrustScoreComputer.ActorScore score = computer.compute(
                List.of(d), Map.of(d.id, List.of(endorsed)), now);

        assertThat(score.attestationPositive()).isEqualTo(1);
        assertThat(score.attestationNegative()).isEqualTo(0);
        assertThat(score.trustScore()).isGreaterThanOrEqualTo(0.5);
    }

    // -------------------------------------------------------------------------
    // CHALLENGED counts as negative
    // -------------------------------------------------------------------------

    @Test
    void challengedCountsAsNegative() {
        final LedgerEntry d = decision("alice", now.minus(1, ChronoUnit.HOURS));
        final LedgerAttestation challenged = attestation(d.id, AttestationVerdict.CHALLENGED);

        final TrustScoreComputer.ActorScore score = computer.compute(
                List.of(d), Map.of(d.id, List.of(challenged)), now);

        assertThat(score.attestationNegative()).isEqualTo(1);
        assertThat(score.overturnedCount()).isEqualTo(1);
        assertThat(score.trustScore()).isLessThanOrEqualTo(0.5);
    }

    // -------------------------------------------------------------------------
    // Score clamping
    // -------------------------------------------------------------------------

    @Test
    void scoreClampedToRange() {
        // Many clean decisions — score must not exceed 1.0
        final LedgerEntry d1 = decision("alice", now.minus(1, ChronoUnit.DAYS));
        final LedgerEntry d2 = decision("alice", now.minus(2, ChronoUnit.DAYS));
        final LedgerEntry d3 = decision("alice", now.minus(3, ChronoUnit.DAYS));
        final LedgerEntry d4 = decision("alice", now.minus(4, ChronoUnit.DAYS));
        final LedgerEntry d5 = decision("alice", now.minus(5, ChronoUnit.DAYS));

        final TrustScoreComputer.ActorScore score = computer.compute(
                List.of(d1, d2, d3, d4, d5), Map.of(), now);

        assertThat(score.trustScore()).isGreaterThanOrEqualTo(0.0);
        assertThat(score.trustScore()).isLessThanOrEqualTo(1.0);
    }
}
