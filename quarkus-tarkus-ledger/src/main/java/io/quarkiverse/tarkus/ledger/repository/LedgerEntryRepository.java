package io.quarkiverse.tarkus.ledger.repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import io.quarkiverse.tarkus.ledger.model.LedgerAttestation;
import io.quarkiverse.tarkus.ledger.model.LedgerEntry;

/**
 * SPI for persisting and querying {@link LedgerEntry} and {@link LedgerAttestation} records.
 *
 * <p>
 * The default implementation uses Hibernate ORM with Panache. Alternative implementations
 * (e.g. in-memory for testing) can be substituted via CDI.
 */
public interface LedgerEntryRepository {

    /**
     * Persist a new ledger entry and return the saved instance.
     *
     * @param entry the ledger entry to persist; must not be {@code null}
     * @return the persisted entry (same instance, post-{@code @PrePersist})
     */
    LedgerEntry save(LedgerEntry entry);

    /**
     * Return all ledger entries for the given WorkItem in sequence order.
     *
     * @param workItemId the WorkItem primary key
     * @return ordered list of ledger entries; empty if none exist
     */
    List<LedgerEntry> findByWorkItemId(UUID workItemId);

    /**
     * Return the most recent ledger entry for the given WorkItem, or empty if none.
     *
     * @param workItemId the WorkItem primary key
     * @return an {@link Optional} containing the latest entry, or empty if no entries exist
     */
    Optional<LedgerEntry> findLatestByWorkItemId(UUID workItemId);

    /**
     * Return a ledger entry by its primary key.
     *
     * @param id the ledger entry UUID primary key
     * @return an {@link Optional} containing the entry if found, or empty if not
     */
    Optional<LedgerEntry> findById(UUID id);

    /**
     * Return all attestations for the given ledger entry, ordered by occurrence time ascending.
     *
     * @param ledgerEntryId the ledger entry UUID to look up attestations for
     * @return ordered list of attestations; empty if none exist
     */
    List<LedgerAttestation> findAttestationsByEntryId(UUID ledgerEntryId);

    /**
     * Persist a new attestation and return the saved instance.
     *
     * @param attestation the attestation to persist; must not be {@code null}
     * @return the persisted attestation (same instance, post-{@code @PrePersist})
     */
    LedgerAttestation saveAttestation(LedgerAttestation attestation);

    /**
     * Return all EVENT-type ledger entries across all WorkItems (for trust score computation).
     *
     * @return list of all EVENT entries; empty if none exist
     */
    List<LedgerEntry> findAllEvents();

    /**
     * Return all attestations for the given set of ledger entry IDs.
     *
     * @param entryIds the set of ledger entry UUIDs to look up attestations for
     * @return map from ledger entry ID to the list of its attestations; empty map if {@code entryIds} is empty
     */
    Map<UUID, List<LedgerAttestation>> findAttestationsForEntries(Set<UUID> entryIds);
}
