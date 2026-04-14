package io.quarkiverse.tarkus.ledger.repository.jpa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.tarkus.ledger.model.LedgerAttestation;
import io.quarkiverse.tarkus.ledger.model.LedgerEntry;
import io.quarkiverse.tarkus.ledger.repository.LedgerEntryRepository;

/**
 * Hibernate ORM / Panache implementation of {@link LedgerEntryRepository}.
 */
@ApplicationScoped
public class JpaLedgerEntryRepository implements LedgerEntryRepository {

    /** {@inheritDoc} */
    @Override
    public LedgerEntry save(final LedgerEntry entry) {
        entry.persist();
        return entry;
    }

    /** {@inheritDoc} */
    @Override
    public List<LedgerEntry> findByWorkItemId(final UUID workItemId) {
        return LedgerEntry.list("workItemId = ?1 ORDER BY sequenceNumber ASC", workItemId);
    }

    /** {@inheritDoc} */
    @Override
    public Optional<LedgerEntry> findLatestByWorkItemId(final UUID workItemId) {
        return LedgerEntry.find("workItemId = ?1 ORDER BY sequenceNumber DESC", workItemId)
                .firstResultOptional();
    }

    /** {@inheritDoc} */
    @Override
    public Optional<LedgerEntry> findById(final UUID id) {
        return Optional.ofNullable(LedgerEntry.findById(id));
    }

    /** {@inheritDoc} */
    @Override
    public List<LedgerAttestation> findAttestationsByEntryId(final UUID ledgerEntryId) {
        return LedgerAttestation.list("ledgerEntryId = ?1 ORDER BY occurredAt ASC", ledgerEntryId);
    }

    /** {@inheritDoc} */
    @Override
    public LedgerAttestation saveAttestation(final LedgerAttestation attestation) {
        attestation.persist();
        return attestation;
    }
}
