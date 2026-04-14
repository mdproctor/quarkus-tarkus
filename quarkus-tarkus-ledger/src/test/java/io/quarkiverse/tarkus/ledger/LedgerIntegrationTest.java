package io.quarkiverse.tarkus.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.quarkiverse.tarkus.ledger.model.ActorType;
import io.quarkiverse.tarkus.ledger.model.AttestationVerdict;
import io.quarkiverse.tarkus.ledger.model.LedgerAttestation;
import io.quarkiverse.tarkus.ledger.model.LedgerEntry;
import io.quarkiverse.tarkus.ledger.model.LedgerEntryType;
import io.quarkiverse.tarkus.ledger.repository.LedgerEntryRepository;
import io.quarkiverse.tarkus.ledger.service.LedgerHashChain;
import io.quarkiverse.tarkus.runtime.model.WorkItemCreateRequest;
import io.quarkiverse.tarkus.runtime.model.WorkItemPriority;
import io.quarkiverse.tarkus.runtime.service.WorkItemService;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Integration tests for the Tarkus Ledger module.
 *
 * <p>
 * Verifies that {@code WorkItemLifecycleEvent} CDI events are observed and
 * converted into {@link LedgerEntry} records with correct field values,
 * sequence numbering, hash chain integrity, and attestation storage.
 *
 * <p>
 * {@code @TestTransaction} rolls back after each test — ledger entries never
 * persist across test boundaries.
 *
 * <p>
 * RED-phase: these tests will not compile until the ledger production classes
 * ({@link LedgerEntry}, {@link LedgerEntryRepository}, etc.) are created.
 */
@QuarkusTest
@TestTransaction
class LedgerIntegrationTest {

    @Inject
    WorkItemService workItemService;

    @Inject
    LedgerEntryRepository ledgerRepo;

    // -------------------------------------------------------------------------
    // Fixture helper
    // -------------------------------------------------------------------------

    private WorkItemCreateRequest basicRequest(final String title) {
        return new WorkItemCreateRequest(title, null, null, null,
                WorkItemPriority.NORMAL, null, null, null, null, "system",
                null, null, null, null);
    }

    // -------------------------------------------------------------------------
    // LedgerEntry written for every lifecycle transition
    // -------------------------------------------------------------------------

    @Test
    void create_writesOneLedgerEntry() {
        final var item = workItemService.create(basicRequest("Create test"));
        final List<LedgerEntry> entries = ledgerRepo.findByWorkItemId(item.id);

        assertThat(entries).hasSize(1);
        final LedgerEntry e = entries.get(0);
        assertThat(e.entryType).isEqualTo(LedgerEntryType.EVENT);
        assertThat(e.eventType).isEqualTo("WorkItemCreated");
        assertThat(e.commandType).isEqualTo("CreateWorkItem");
        assertThat(e.actorId).isEqualTo("system");
    }

    @Test
    void claim_writesLedgerEntry() {
        final var item = workItemService.create(basicRequest("Claim test"));
        workItemService.claim(item.id, "alice");

        final List<LedgerEntry> entries = ledgerRepo.findByWorkItemId(item.id);
        assertThat(entries).hasSize(2);

        final LedgerEntry claim = entries.get(1);
        assertThat(claim.eventType).isEqualTo("WorkItemAssigned");
        assertThat(claim.actorId).isEqualTo("alice");
        assertThat(claim.actorRole).isEqualTo("Claimant");
    }

    @Test
    void start_writesLedgerEntry() {
        final var item = workItemService.create(basicRequest("Start test"));
        workItemService.claim(item.id, "alice");
        workItemService.start(item.id, "alice");

        final List<LedgerEntry> entries = ledgerRepo.findByWorkItemId(item.id);
        assertThat(entries).hasSize(3);

        final LedgerEntry start = entries.get(2);
        assertThat(start.eventType).isEqualTo("WorkItemStarted");
        assertThat(start.commandType).isEqualTo("StartWorkItem");
    }

    @Test
    void complete_writesLedgerEntry() {
        final var item = workItemService.create(basicRequest("Complete test"));
        workItemService.claim(item.id, "alice");
        workItemService.start(item.id, "alice");
        workItemService.complete(item.id, "alice", "All done");

        final List<LedgerEntry> entries = ledgerRepo.findByWorkItemId(item.id);
        assertThat(entries).hasSize(4);

        final LedgerEntry complete = entries.get(3);
        assertThat(complete.eventType).isEqualTo("WorkItemCompleted");
        assertThat(complete.commandType).isEqualTo("CompleteWorkItem");
    }

    @Test
    void reject_writesLedgerEntry() {
        final var item = workItemService.create(basicRequest("Reject test"));
        workItemService.claim(item.id, "alice");
        workItemService.reject(item.id, "alice", "Not my responsibility");

        final List<LedgerEntry> entries = ledgerRepo.findByWorkItemId(item.id);
        assertThat(entries).hasSize(3);

        final LedgerEntry reject = entries.get(2);
        assertThat(reject.eventType).isEqualTo("WorkItemRejected");
    }

    @Test
    void delegate_writesLedgerEntry() {
        final var item = workItemService.create(basicRequest("Delegate test"));
        workItemService.claim(item.id, "alice");
        workItemService.delegate(item.id, "alice", "bob");

        final List<LedgerEntry> entries = ledgerRepo.findByWorkItemId(item.id);
        assertThat(entries).hasSize(3);

        final LedgerEntry delegate = entries.get(2);
        assertThat(delegate.eventType).isEqualTo("WorkItemDelegated");
        assertThat(delegate.actorRole).isEqualTo("Delegator");
    }

    @Test
    void cancel_writesLedgerEntry() {
        final var item = workItemService.create(basicRequest("Cancel test"));
        workItemService.cancel(item.id, "admin", "No longer needed");

        final List<LedgerEntry> entries = ledgerRepo.findByWorkItemId(item.id);
        assertThat(entries).hasSize(2);

        final LedgerEntry cancel = entries.get(1);
        assertThat(cancel.eventType).isEqualTo("WorkItemCancelled");
    }

    // -------------------------------------------------------------------------
    // sequenceNumber
    // -------------------------------------------------------------------------

    @Test
    void sequenceNumber_incrementsPerWorkItem() {
        final var item = workItemService.create(basicRequest("Sequence test"));
        workItemService.claim(item.id, "alice");
        workItemService.start(item.id, "alice");
        workItemService.complete(item.id, "alice", "Done");

        final List<LedgerEntry> entries = ledgerRepo.findByWorkItemId(item.id);
        assertThat(entries).hasSize(4);
        assertThat(entries.get(0).sequenceNumber).isEqualTo(1);
        assertThat(entries.get(1).sequenceNumber).isEqualTo(2);
        assertThat(entries.get(2).sequenceNumber).isEqualTo(3);
        assertThat(entries.get(3).sequenceNumber).isEqualTo(4);
    }

    @Test
    void sequenceNumber_independentAcrossWorkItems() {
        final var item1 = workItemService.create(basicRequest("Item 1"));
        final var item2 = workItemService.create(basicRequest("Item 2"));

        // Each item should independently start at sequence 1
        final List<LedgerEntry> entries1 = ledgerRepo.findByWorkItemId(item1.id);
        final List<LedgerEntry> entries2 = ledgerRepo.findByWorkItemId(item2.id);

        assertThat(entries1).hasSize(1);
        assertThat(entries2).hasSize(1);
        assertThat(entries1.get(0).sequenceNumber).isEqualTo(1);
        assertThat(entries2.get(0).sequenceNumber).isEqualTo(1);
    }

    // -------------------------------------------------------------------------
    // Hash chain
    // -------------------------------------------------------------------------

    @Test
    void hashChain_firstEntryHasNullPreviousHash() {
        final var item = workItemService.create(basicRequest("Hash test 1"));

        final List<LedgerEntry> entries = ledgerRepo.findByWorkItemId(item.id);
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).previousHash).isNull();
    }

    @Test
    void hashChain_secondEntryPreviousHashEqualsFirstDigest() {
        final var item = workItemService.create(basicRequest("Hash test 2"));
        workItemService.claim(item.id, "alice");

        final List<LedgerEntry> entries = ledgerRepo.findByWorkItemId(item.id);
        assertThat(entries).hasSize(2);
        assertThat(entries.get(1).previousHash).isEqualTo(entries.get(0).digest);
    }

    @Test
    void hashChain_fullPathChainIsValid() {
        final var item = workItemService.create(basicRequest("Hash verify test"));
        workItemService.claim(item.id, "alice");
        workItemService.start(item.id, "alice");
        workItemService.complete(item.id, "alice", "Verified");

        final List<LedgerEntry> entries = ledgerRepo.findByWorkItemId(item.id);
        assertThat(entries).hasSize(4);
        assertThat(LedgerHashChain.verify(entries)).isTrue();
    }

    // -------------------------------------------------------------------------
    // decisionContext
    // -------------------------------------------------------------------------

    @Test
    void decisionContext_capturedOnCreate() {
        final var item = workItemService.create(basicRequest("DecisionContext create test"));

        final List<LedgerEntry> entries = ledgerRepo.findByWorkItemId(item.id);
        assertThat(entries).hasSize(1);

        final LedgerEntry e = entries.get(0);
        assertThat(e.decisionContext).isNotNull();
        assertThat(e.decisionContext).containsIgnoringCase("PENDING");
    }

    @Test
    void decisionContext_capturedOnComplete() {
        final var item = workItemService.create(basicRequest("DecisionContext complete test"));
        workItemService.claim(item.id, "alice");
        workItemService.start(item.id, "alice");
        workItemService.complete(item.id, "alice", "Finished");

        final List<LedgerEntry> entries = ledgerRepo.findByWorkItemId(item.id);
        assertThat(entries).hasSize(4);

        final LedgerEntry completed = entries.get(3);
        assertThat(completed.decisionContext).isNotNull();
        assertThat(completed.decisionContext).containsIgnoringCase("COMPLETED");
    }

    // -------------------------------------------------------------------------
    // commandType / eventType mapping
    // -------------------------------------------------------------------------

    @Test
    void commandAndEventType_matchExpectedMapping() {
        final var item = workItemService.create(basicRequest("Mapping test"));
        workItemService.claim(item.id, "bob");

        final List<LedgerEntry> entries = ledgerRepo.findByWorkItemId(item.id);
        assertThat(entries).hasSize(2);

        // Create entry
        final LedgerEntry create = entries.get(0);
        assertThat(create.commandType).isEqualTo("CreateWorkItem");
        assertThat(create.eventType).isEqualTo("WorkItemCreated");

        // Claim entry
        final LedgerEntry claim = entries.get(1);
        assertThat(claim.commandType).isEqualTo("ClaimWorkItem");
        assertThat(claim.eventType).isEqualTo("WorkItemAssigned");
    }

    // -------------------------------------------------------------------------
    // Feature flag: hash chain enabled (disabled case is covered by LedgerHashChainTest)
    // -------------------------------------------------------------------------

    @Test
    void hashChainEnabled_digestIsNotNull() {
        // When hash-chain is enabled (default in test application.properties),
        // every entry must carry a non-null digest.
        final var item = workItemService.create(basicRequest("Digest non-null test"));

        final List<LedgerEntry> entries = ledgerRepo.findByWorkItemId(item.id);
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).digest).isNotNull();
    }

    // -------------------------------------------------------------------------
    // Attestation
    // -------------------------------------------------------------------------

    @Test
    void attestation_savedAndRetrievable() {
        final var item = workItemService.create(basicRequest("Attestation test"));
        final List<LedgerEntry> entries = ledgerRepo.findByWorkItemId(item.id);
        assertThat(entries).hasSize(1);
        final UUID entryId = entries.get(0).id;

        final LedgerAttestation attestation = new LedgerAttestation();
        attestation.ledgerEntryId = entryId;
        attestation.workItemId = item.id;
        attestation.attestorId = "alice";
        attestation.attestorType = ActorType.HUMAN;
        attestation.verdict = AttestationVerdict.SOUND;
        attestation.evidence = "All checks passed";
        attestation.confidence = 0.9;
        ledgerRepo.saveAttestation(attestation);

        final List<LedgerAttestation> results = ledgerRepo.findAttestationsByEntryId(entryId);
        assertThat(results).hasSize(1);

        final LedgerAttestation saved = results.get(0);
        assertThat(saved.attestorId).isEqualTo("alice");
        assertThat(saved.attestorType).isEqualTo(ActorType.HUMAN);
        assertThat(saved.verdict).isEqualTo(AttestationVerdict.SOUND);
        assertThat(saved.confidence).isEqualTo(0.9);
    }

    @Test
    void attestation_multipleAttestationsOnSameEntry() {
        final var item = workItemService.create(basicRequest("Multi-attestation test"));
        final List<LedgerEntry> entries = ledgerRepo.findByWorkItemId(item.id);
        final UUID entryId = entries.get(0).id;

        final LedgerAttestation a1 = new LedgerAttestation();
        a1.ledgerEntryId = entryId;
        a1.workItemId = item.id;
        a1.attestorId = "alice";
        a1.attestorType = ActorType.HUMAN;
        a1.verdict = AttestationVerdict.SOUND;
        a1.confidence = 0.9;
        ledgerRepo.saveAttestation(a1);

        final LedgerAttestation a2 = new LedgerAttestation();
        a2.ledgerEntryId = entryId;
        a2.workItemId = item.id;
        a2.attestorId = "audit-agent";
        a2.attestorType = ActorType.AGENT;
        a2.verdict = AttestationVerdict.ENDORSED;
        a2.confidence = 0.95;
        ledgerRepo.saveAttestation(a2);

        final List<LedgerAttestation> results = ledgerRepo.findAttestationsByEntryId(entryId);
        assertThat(results).hasSize(2);

        final List<String> attestorIds = results.stream().map(a -> a.attestorId).toList();
        assertThat(attestorIds).containsExactlyInAnyOrder("alice", "audit-agent");
    }
}
