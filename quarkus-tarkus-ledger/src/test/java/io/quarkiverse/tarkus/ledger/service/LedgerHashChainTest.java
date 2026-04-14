package io.quarkiverse.tarkus.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.quarkiverse.tarkus.ledger.model.LedgerEntry;
import io.quarkiverse.tarkus.ledger.model.LedgerEntryType;

/**
 * Pure JUnit 5 unit tests for {@link LedgerHashChain} — no Quarkus runtime, no CDI.
 *
 * <p>
 * RED-phase: these tests will not compile until the production classes
 * ({@link LedgerHashChain}, {@link LedgerEntry}, {@link LedgerEntryType}) are created.
 * That is the correct TDD state.
 */
class LedgerHashChainTest {

    // -------------------------------------------------------------------------
    // Fixture helper
    // -------------------------------------------------------------------------

    private LedgerEntry entry(final UUID workItemId, final int seq) {
        final LedgerEntry e = new LedgerEntry();
        e.workItemId = workItemId;
        e.sequenceNumber = seq;
        e.entryType = LedgerEntryType.EVENT;
        e.commandType = "CreateWorkItem";
        e.eventType = "WorkItemCreated";
        e.actorId = "system";
        e.actorRole = "Initiator";
        e.planRef = null;
        e.occurredAt = Instant.now();
        return e;
    }

    // -------------------------------------------------------------------------
    // compute — basic contract
    // -------------------------------------------------------------------------

    @Test
    void compute_returnsNonNullDigest() {
        final UUID id = UUID.randomUUID();
        final LedgerEntry e = entry(id, 1);

        final String digest = LedgerHashChain.compute(null, e);

        assertThat(digest).isNotNull();
    }

    @Test
    void compute_withGenesisPrevious_isDeterministic() {
        final UUID id = UUID.randomUUID();
        final LedgerEntry e = entry(id, 1);
        // Fix occurredAt so the entry is identical on both calls
        e.occurredAt = Instant.parse("2026-04-14T10:00:00Z");

        final String first = LedgerHashChain.compute(null, e);
        final String second = LedgerHashChain.compute(null, e);

        assertThat(first).isEqualTo(second);
    }

    @Test
    void compute_differentPreviousHash_producesDifferentDigest() {
        final UUID id = UUID.randomUUID();
        final LedgerEntry e = entry(id, 2);
        e.occurredAt = Instant.parse("2026-04-14T10:00:00Z");

        final String withNull = LedgerHashChain.compute(null, e);
        final String withPrev = LedgerHashChain.compute("abc123", e);

        assertThat(withNull).isNotEqualTo(withPrev);
    }

    @Test
    void compute_mutatingField_changesDifferentDigest() {
        final UUID id = UUID.randomUUID();
        final LedgerEntry e = entry(id, 1);
        e.occurredAt = Instant.parse("2026-04-14T10:00:00Z");

        final String before = LedgerHashChain.compute(null, e);

        e.actorId = "mutated-actor";
        final String after = LedgerHashChain.compute(null, e);

        assertThat(before).isNotEqualTo(after);
    }

    // -------------------------------------------------------------------------
    // verify — empty and single-entry chains
    // -------------------------------------------------------------------------

    @Test
    void verify_emptyList_returnsTrue() {
        assertThat(LedgerHashChain.verify(Collections.emptyList())).isTrue();
    }

    @Test
    void verify_singleEntry_withNullPrevious_returnsTrue() {
        final UUID id = UUID.randomUUID();
        final LedgerEntry e = entry(id, 1);
        e.previousHash = null;
        e.digest = LedgerHashChain.compute(null, e);

        assertThat(LedgerHashChain.verify(List.of(e))).isTrue();
    }

    // -------------------------------------------------------------------------
    // verify — two-entry chains
    // -------------------------------------------------------------------------

    @Test
    void verify_twoEntries_validChain_returnsTrue() {
        final UUID id = UUID.randomUUID();

        final LedgerEntry e1 = entry(id, 1);
        e1.previousHash = null;
        e1.digest = LedgerHashChain.compute(null, e1);

        final LedgerEntry e2 = entry(id, 2);
        e2.commandType = "ClaimWorkItem";
        e2.eventType = "WorkItemAssigned";
        e2.actorId = "alice";
        e2.previousHash = e1.digest;
        e2.digest = LedgerHashChain.compute(e1.digest, e2);

        assertThat(LedgerHashChain.verify(List.of(e1, e2))).isTrue();
    }

    @Test
    void verify_twoEntries_tamperedSecondEntry_returnsFalse() {
        final UUID id = UUID.randomUUID();

        final LedgerEntry e1 = entry(id, 1);
        e1.previousHash = null;
        e1.digest = LedgerHashChain.compute(null, e1);

        final LedgerEntry e2 = entry(id, 2);
        e2.commandType = "ClaimWorkItem";
        e2.eventType = "WorkItemAssigned";
        e2.actorId = "alice";
        e2.previousHash = e1.digest;
        e2.digest = LedgerHashChain.compute(e1.digest, e2);

        // Tamper: mutate actorId without recomputing digest
        e2.actorId = "mallory";

        assertThat(LedgerHashChain.verify(List.of(e1, e2))).isFalse();
    }

    // -------------------------------------------------------------------------
    // genesisHash sentinel
    // -------------------------------------------------------------------------

    @Test
    void genesisHash_returnsGENESIS() {
        assertThat(LedgerHashChain.genesisHash()).isEqualTo("GENESIS");
    }
}
