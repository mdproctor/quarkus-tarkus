package io.quarkiverse.work.ledger.repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import io.quarkiverse.ledger.runtime.model.LedgerAttestation;
import io.quarkiverse.ledger.runtime.repository.LedgerEntryRepository;
import io.quarkiverse.work.ledger.model.WorkItemLedgerEntry;

/**
 * Typed repository for {@link WorkItemLedgerEntry} records.
 *
 * <p>
 * Extends {@link LedgerEntryRepository} with WorkItem-specific query methods that
 * return concrete {@link WorkItemLedgerEntry} instances rather than the base type.
 * Alias methods ({@code findByWorkItemId}, {@code findLatestByWorkItemId}) delegate to the
 * base {@code findBySubjectId} / {@code findLatestBySubjectId} with typed results.
 */
public interface WorkItemLedgerEntryRepository extends LedgerEntryRepository {

    /**
     * Return all ledger entries for the given WorkItem in ascending sequence order.
     *
     * @param workItemId the WorkItem UUID
     * @return ordered list of typed entries; empty if none exist
     */
    List<WorkItemLedgerEntry> findByWorkItemId(UUID workItemId);

    /**
     * Return the most recent ledger entry for the given WorkItem, or empty if none.
     *
     * @param workItemId the WorkItem UUID
     * @return the latest typed entry, or empty if no entries exist
     */
    Optional<WorkItemLedgerEntry> findLatestByWorkItemId(UUID workItemId);

    /**
     * Persist a new attestation and return the saved instance.
     * Overrides to be explicitly available on this typed interface.
     *
     * @param attestation the attestation to persist; must not be {@code null}
     * @return the persisted attestation
     */
    @Override
    LedgerAttestation saveAttestation(LedgerAttestation attestation);

    /**
     * Return all attestations for the given ledger entry, ordered by occurrence time ascending.
     *
     * @param ledgerEntryId the ledger entry UUID
     * @return ordered list of attestations; empty if none exist
     */
    @Override
    List<LedgerAttestation> findAttestationsByEntryId(UUID ledgerEntryId);

    /**
     * Return all attestations for the given set of ledger entry IDs, grouped by entry ID.
     *
     * @param entryIds the set of ledger entry UUIDs
     * @return map from entry ID to its attestations; empty map if {@code entryIds} is empty
     */
    @Override
    Map<UUID, List<LedgerAttestation>> findAttestationsForEntries(Set<UUID> entryIds);
}
