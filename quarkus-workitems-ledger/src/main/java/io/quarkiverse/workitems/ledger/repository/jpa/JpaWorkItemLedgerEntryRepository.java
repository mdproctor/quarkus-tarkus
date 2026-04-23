package io.quarkiverse.workitems.ledger.repository.jpa;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import io.quarkiverse.ledger.runtime.model.LedgerAttestation;
import io.quarkiverse.ledger.runtime.model.LedgerEntry;
import io.quarkiverse.ledger.runtime.model.LedgerEntryType;
import io.quarkiverse.workitems.ledger.model.WorkItemLedgerEntry;
import io.quarkiverse.workitems.ledger.repository.WorkItemLedgerEntryRepository;

/**
 * Hibernate ORM / EntityManager implementation of {@link WorkItemLedgerEntryRepository}.
 *
 * <p>
 * Uses EntityManager directly — {@link LedgerEntry} and its subclasses are plain JPA
 * entities (not Panache), so all queries go through JPQL or named queries.
 */
@ApplicationScoped
public class JpaWorkItemLedgerEntryRepository implements WorkItemLedgerEntryRepository {

    @Inject
    EntityManager em;

    /** {@inheritDoc} */
    @Override
    public LedgerEntry save(final LedgerEntry entry) {
        em.persist(entry);
        return entry;
    }

    /** {@inheritDoc} */
    @Override
    public List<WorkItemLedgerEntry> findByWorkItemId(final UUID workItemId) {
        return em.createQuery(
                "SELECT e FROM WorkItemLedgerEntry e WHERE e.subjectId = :subjectId ORDER BY e.sequenceNumber ASC",
                WorkItemLedgerEntry.class)
                .setParameter("subjectId", workItemId)
                .getResultList();
    }

    /** {@inheritDoc} */
    @Override
    public Optional<WorkItemLedgerEntry> findLatestByWorkItemId(final UUID workItemId) {
        return em.createQuery(
                "SELECT e FROM WorkItemLedgerEntry e WHERE e.subjectId = :subjectId ORDER BY e.sequenceNumber DESC",
                WorkItemLedgerEntry.class)
                .setParameter("subjectId", workItemId)
                .setMaxResults(1)
                .getResultStream()
                .findFirst();
    }

    /** {@inheritDoc} */
    @Override
    public List<LedgerEntry> findBySubjectId(final UUID subjectId) {
        return em.createQuery(
                "SELECT e FROM LedgerEntry e WHERE e.subjectId = :subjectId ORDER BY e.sequenceNumber ASC",
                LedgerEntry.class)
                .setParameter("subjectId", subjectId)
                .getResultList();
    }

    /** {@inheritDoc} */
    @Override
    public Optional<LedgerEntry> findLatestBySubjectId(final UUID subjectId) {
        return em.createQuery(
                "SELECT e FROM LedgerEntry e WHERE e.subjectId = :subjectId ORDER BY e.sequenceNumber DESC",
                LedgerEntry.class)
                .setParameter("subjectId", subjectId)
                .setMaxResults(1)
                .getResultStream()
                .findFirst();
    }

    /** {@inheritDoc} */
    @Override
    public Optional<LedgerEntry> findEntryById(final UUID id) {
        return Optional.ofNullable(em.find(LedgerEntry.class, id));
    }

    /** {@inheritDoc} */
    @Override
    public List<LedgerAttestation> findAttestationsByEntryId(final UUID ledgerEntryId) {
        return em.createNamedQuery("LedgerAttestation.findByEntryId", LedgerAttestation.class)
                .setParameter("entryId", ledgerEntryId)
                .getResultList();
    }

    /** {@inheritDoc} */
    @Override
    public LedgerAttestation saveAttestation(final LedgerAttestation attestation) {
        em.persist(attestation);
        return attestation;
    }

    /** {@inheritDoc} */
    @Override
    public List<LedgerEntry> listAll() {
        return em.createQuery("SELECT e FROM LedgerEntry e ORDER BY e.occurredAt ASC", LedgerEntry.class)
                .getResultList();
    }

    /** {@inheritDoc} */
    @Override
    public List<LedgerEntry> findAllEvents() {
        return em.createQuery(
                "SELECT e FROM LedgerEntry e WHERE e.entryType = :type ORDER BY e.occurredAt ASC",
                LedgerEntry.class)
                .setParameter("type", LedgerEntryType.EVENT)
                .getResultList();
    }

    /** {@inheritDoc} */
    @Override
    public Map<UUID, List<LedgerAttestation>> findAttestationsForEntries(final Set<UUID> entryIds) {
        if (entryIds.isEmpty()) {
            return Collections.emptyMap();
        }
        final List<LedgerAttestation> all = em
                .createNamedQuery("LedgerAttestation.findByEntryIds", LedgerAttestation.class)
                .setParameter("entryIds", entryIds)
                .getResultList();
        return all.stream().collect(Collectors.groupingBy(a -> a.ledgerEntryId));
    }

    /** {@inheritDoc} */
    @Override
    public List<LedgerEntry> findByTimeRange(final Instant from, final Instant to) {
        return em.createQuery(
                "SELECT e FROM LedgerEntry e WHERE e.occurredAt >= :from AND e.occurredAt <= :to ORDER BY e.occurredAt ASC",
                LedgerEntry.class)
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList();
    }

    /** {@inheritDoc} */
    @Override
    public List<LedgerEntry> findCausedBy(final UUID causedByEntryId) {
        // ObservabilitySupplement.causedByEntryId stored in supplement JSON — not yet queryable via JPQL
        return List.of();
    }

    /** {@inheritDoc} */
    @Override
    public List<LedgerEntry> findByActorId(final String actorId, final Instant from, final Instant to) {
        return em.createQuery(
                "SELECT e FROM LedgerEntry e WHERE e.actorId = :actorId AND e.occurredAt >= :from AND e.occurredAt <= :to ORDER BY e.occurredAt ASC",
                LedgerEntry.class)
                .setParameter("actorId", actorId)
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList();
    }

    /** {@inheritDoc} */
    @Override
    public List<LedgerEntry> findByActorRole(final String actorRole, final Instant from, final Instant to) {
        return em.createQuery(
                "SELECT e FROM LedgerEntry e WHERE e.actorRole = :actorRole AND e.occurredAt >= :from AND e.occurredAt <= :to ORDER BY e.occurredAt ASC",
                LedgerEntry.class)
                .setParameter("actorRole", actorRole)
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList();
    }
}
