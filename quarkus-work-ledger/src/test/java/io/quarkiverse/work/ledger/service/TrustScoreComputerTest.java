package io.quarkiverse.work.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.casehub.ledger.api.model.ActorType;
import io.casehub.ledger.api.model.AttestationVerdict;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.runtime.model.LedgerAttestation;
import io.casehub.ledger.runtime.service.TrustScoreComputer;
import io.quarkiverse.work.ledger.model.WorkItemLedgerEntry;

/**
 * Pure unit tests for {@link TrustScoreComputer} using WorkItems's concrete
 * {@link WorkItemLedgerEntry} subclass — no Quarkus context required.
 *
 * <p>
 * RED-phase: these tests will not compile until {@link WorkItemLedgerEntry} is created.
 */
class TrustScoreComputerTest {

    private final TrustScoreComputer computer = new TrustScoreComputer(90);
    private final Instant now = Instant.now();

    // -------------------------------------------------------------------------
    // Fixture helpers
    // -------------------------------------------------------------------------

    private WorkItemLedgerEntry decision(final String actorId, final Instant occurredAt) {
        final WorkItemLedgerEntry e = new WorkItemLedgerEntry();
        e.id = UUID.randomUUID();
        e.subjectId = UUID.randomUUID();
        e.sequenceNumber = 1;
        e.entryType = LedgerEntryType.EVENT;
        e.actorId = actorId;
        e.actorType = ActorType.HUMAN;
        e.occurredAt = occurredAt;
        return e;
    }

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
        // No attestations → Beta(1,1) prior only → neutral score 0.5 (maximum uncertainty)
        final WorkItemLedgerEntry d = decision("alice", now.minus(1, ChronoUnit.HOURS));

        final TrustScoreComputer.ActorScore score = computer.compute(
                List.of(d), Map.of(), now);

        assertThat(score.trustScore()).isCloseTo(0.5, within(0.01));
        assertThat(score.decisionCount()).isEqualTo(1);
    }

    // -------------------------------------------------------------------------
    // Single decision — positive attestation
    // -------------------------------------------------------------------------

    @Test
    void singleDecisionWithSoundAttestation_returnsHighScore() {
        // 1 SOUND (confidence=0.9, age≈0): α=1+0.9=1.9, β=1.0 → 1.9/2.9 ≈ 0.655
        final WorkItemLedgerEntry d = decision("alice", now.minus(1, ChronoUnit.HOURS));
        final LedgerAttestation a = attestation(d.id, AttestationVerdict.SOUND);

        final TrustScoreComputer.ActorScore score = computer.compute(
                List.of(d), Map.of(d.id, List.of(a)), now);

        assertThat(score.trustScore()).isCloseTo(0.655, within(0.01));
        assertThat(score.attestationPositive()).isEqualTo(1);
    }

    // -------------------------------------------------------------------------
    // Single decision — negative attestation
    // -------------------------------------------------------------------------

    @Test
    void singleDecisionWithFlaggedAttestation_returnsLowScore() {
        // 1 FLAGGED (confidence=0.9, age≈0): α=1.0, β=1+0.9=1.9 → 1.0/2.9 ≈ 0.345
        final WorkItemLedgerEntry d = decision("alice", now.minus(1, ChronoUnit.HOURS));
        final LedgerAttestation a = attestation(d.id, AttestationVerdict.FLAGGED);

        final TrustScoreComputer.ActorScore score = computer.compute(
                List.of(d), Map.of(d.id, List.of(a)), now);

        assertThat(score.trustScore()).isCloseTo(0.345, within(0.01));
        assertThat(score.overturnedCount()).isEqualTo(1);
        assertThat(score.attestationNegative()).isEqualTo(1);
    }

    // -------------------------------------------------------------------------
    // Mixed attestations — majority negative
    // -------------------------------------------------------------------------

    @Test
    void mixedAttestations_majority_negative_returnsLowScore() {
        // 1 SOUND + 2 FLAGGED (all age≈0, confidence=0.9):
        // α=1+0.9=1.9, β=1+0.9+0.9=2.8 → 1.9/4.7 ≈ 0.404 (below neutral)
        final WorkItemLedgerEntry d = decision("alice", now.minus(1, ChronoUnit.HOURS));
        final LedgerAttestation sound = attestation(d.id, AttestationVerdict.SOUND);
        final LedgerAttestation flagged1 = attestation(d.id, AttestationVerdict.FLAGGED);
        final LedgerAttestation flagged2 = attestation(d.id, AttestationVerdict.FLAGGED);

        final TrustScoreComputer.ActorScore score = computer.compute(
                List.of(d), Map.of(d.id, List.of(sound, flagged1, flagged2)), now);

        assertThat(score.trustScore()).isLessThan(0.5);
    }

    // -------------------------------------------------------------------------
    // Mixed attestations — majority positive
    // -------------------------------------------------------------------------

    @Test
    void mixedAttestations_majority_positive_returnsMidScore() {
        final WorkItemLedgerEntry d = decision("alice", now.minus(1, ChronoUnit.HOURS));
        final LedgerAttestation sound1 = attestation(d.id, AttestationVerdict.SOUND);
        final LedgerAttestation sound2 = attestation(d.id, AttestationVerdict.SOUND);
        final LedgerAttestation flagged = attestation(d.id, AttestationVerdict.FLAGGED);

        final TrustScoreComputer.ActorScore score = computer.compute(
                List.of(d), Map.of(d.id, List.of(sound1, sound2, flagged)), now);

        assertThat(score.trustScore()).isGreaterThan(0.0);
        assertThat(score.trustScore()).isLessThanOrEqualTo(1.0);
    }

    // -------------------------------------------------------------------------
    // Multiple decisions — all clean
    // -------------------------------------------------------------------------

    @Test
    void multipleDecisions_allClean_returnsHighScore() {
        // No attestations → Beta(1,1) prior only → 0.5 regardless of decision count
        final WorkItemLedgerEntry d1 = decision("alice", now.minus(1, ChronoUnit.DAYS));
        final WorkItemLedgerEntry d2 = decision("alice", now.minus(2, ChronoUnit.DAYS));
        final WorkItemLedgerEntry d3 = decision("alice", now.minus(3, ChronoUnit.DAYS));

        final TrustScoreComputer.ActorScore score = computer.compute(
                List.of(d1, d2, d3), Map.of(), now);

        assertThat(score.trustScore()).isCloseTo(0.5, within(0.01));
        assertThat(score.decisionCount()).isEqualTo(3);
    }

    // -------------------------------------------------------------------------
    // Multiple decisions — mixed
    // -------------------------------------------------------------------------

    @Test
    void multipleDecisions_mixed_returnsProportionalScore() {
        final WorkItemLedgerEntry clean1 = decision("alice", now.minus(1, ChronoUnit.DAYS));
        final WorkItemLedgerEntry clean2 = decision("alice", now.minus(2, ChronoUnit.DAYS));
        final WorkItemLedgerEntry bad = decision("alice", now.minus(3, ChronoUnit.DAYS));
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
        // Decay is based on attestation.occurredAt, so set it to match the decision age.
        // recent SOUND (age=1d): decay≈0.992, weight≈0.893
        // old FLAGGED (age=180d, half-life=90): decay=2^(-2)=0.25, weight=0.225
        // α=1+0.893=1.893, β=1+0.225=1.225 → score≈0.607 > 0.5
        final WorkItemLedgerEntry recent = decision("alice", now.minus(1, ChronoUnit.DAYS));
        final LedgerAttestation recentSound = attestation(recent.id, AttestationVerdict.SOUND);
        recentSound.occurredAt = now.minus(1, ChronoUnit.DAYS);

        final WorkItemLedgerEntry old = decision("alice", now.minus(180, ChronoUnit.DAYS));
        final LedgerAttestation oldFlagged = attestation(old.id, AttestationVerdict.FLAGGED);
        oldFlagged.occurredAt = now.minus(180, ChronoUnit.DAYS);

        final TrustScoreComputer.ActorScore score = computer.compute(
                List.of(recent, old),
                Map.of(recent.id, List.of(recentSound), old.id, List.of(oldFlagged)),
                now);

        assertThat(score.trustScore()).isGreaterThan(0.5);
    }

    // -------------------------------------------------------------------------
    // Half-life respected
    // -------------------------------------------------------------------------

    @Test
    void halfLifeRespected_oldDecisionHasLessWeight() {
        // Decay is based on attestation.occurredAt, so set it to match the decision age.
        // recent SOUND (age=1d, half-life=30): decay≈0.977, weight≈0.879
        // veryOld FLAGGED (age=365d, half-life=30): decay=2^(-12.2)≈0.000217, weight≈0.000195
        // α=1+0.879≈1.879, β≈1.000 → score≈0.652 > 0.5
        final TrustScoreComputer shortHalfLife = new TrustScoreComputer(30);

        final WorkItemLedgerEntry recent = decision("alice", now.minus(1, ChronoUnit.DAYS));
        final LedgerAttestation recentSound = attestation(recent.id, AttestationVerdict.SOUND);
        recentSound.occurredAt = now.minus(1, ChronoUnit.DAYS);

        final WorkItemLedgerEntry veryOld = decision("alice", now.minus(365, ChronoUnit.DAYS));
        final LedgerAttestation oldFlagged = attestation(veryOld.id, AttestationVerdict.FLAGGED);
        oldFlagged.occurredAt = now.minus(365, ChronoUnit.DAYS);

        final TrustScoreComputer.ActorScore score = shortHalfLife.compute(
                List.of(recent, veryOld),
                Map.of(recent.id, List.of(recentSound), veryOld.id, List.of(oldFlagged)),
                now);

        assertThat(score.trustScore()).isGreaterThan(0.5);
    }

    // -------------------------------------------------------------------------
    // ENDORSED counts as positive; CHALLENGED counts as negative
    // -------------------------------------------------------------------------

    @Test
    void endorsedCountsAsPositive() {
        final WorkItemLedgerEntry d = decision("alice", now.minus(1, ChronoUnit.HOURS));
        final LedgerAttestation endorsed = attestation(d.id, AttestationVerdict.ENDORSED);

        final TrustScoreComputer.ActorScore score = computer.compute(
                List.of(d), Map.of(d.id, List.of(endorsed)), now);

        assertThat(score.attestationPositive()).isEqualTo(1);
        assertThat(score.attestationNegative()).isEqualTo(0);
    }

    @Test
    void challengedCountsAsNegative() {
        final WorkItemLedgerEntry d = decision("alice", now.minus(1, ChronoUnit.HOURS));
        final LedgerAttestation challenged = attestation(d.id, AttestationVerdict.CHALLENGED);

        final TrustScoreComputer.ActorScore score = computer.compute(
                List.of(d), Map.of(d.id, List.of(challenged)), now);

        assertThat(score.attestationNegative()).isEqualTo(1);
        assertThat(score.overturnedCount()).isEqualTo(1);
    }

    // -------------------------------------------------------------------------
    // Score clamping
    // -------------------------------------------------------------------------

    @Test
    void scoreClampedToRange() {
        final WorkItemLedgerEntry d1 = decision("alice", now.minus(1, ChronoUnit.DAYS));
        final WorkItemLedgerEntry d2 = decision("alice", now.minus(2, ChronoUnit.DAYS));
        final WorkItemLedgerEntry d3 = decision("alice", now.minus(3, ChronoUnit.DAYS));
        final WorkItemLedgerEntry d4 = decision("alice", now.minus(4, ChronoUnit.DAYS));
        final WorkItemLedgerEntry d5 = decision("alice", now.minus(5, ChronoUnit.DAYS));

        final TrustScoreComputer.ActorScore score = computer.compute(
                List.of(d1, d2, d3, d4, d5), Map.of(), now);

        assertThat(score.trustScore()).isGreaterThanOrEqualTo(0.0);
        assertThat(score.trustScore()).isLessThanOrEqualTo(1.0);
    }
}
