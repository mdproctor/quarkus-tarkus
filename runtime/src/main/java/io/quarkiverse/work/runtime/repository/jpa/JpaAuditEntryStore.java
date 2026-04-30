package io.casehub.work.runtime.repository.jpa;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import io.casehub.work.runtime.model.AuditEntry;
import io.casehub.work.runtime.repository.AuditEntryStore;
import io.casehub.work.runtime.repository.AuditQuery;

/**
 * Default JPA/Panache implementation of {@link AuditEntryStore}.
 */
@ApplicationScoped
public class JpaAuditEntryStore implements AuditEntryStore {

    @Inject
    EntityManager em;

    @Override
    public void append(final AuditEntry entry) {
        entry.persist();
    }

    @Override
    public List<AuditEntry> findByWorkItemId(final UUID workItemId) {
        return AuditEntry.list("workItemId = ?1 ORDER BY occurredAt ASC", workItemId);
    }

    @Override
    public List<AuditEntry> query(final AuditQuery query) {
        final String jpql = buildJpql(query, false);
        final TypedQuery<AuditEntry> q = em.createQuery(jpql, AuditEntry.class);
        applyParams(q, query);
        q.setFirstResult(query.page() * query.size());
        q.setMaxResults(query.size());
        return q.getResultList();
    }

    @Override
    public long count(final AuditQuery query) {
        final String jpql = buildJpql(query, true);
        final TypedQuery<Long> q = em.createQuery(jpql, Long.class);
        applyParams(q, query);
        return q.getSingleResult();
    }

    private String buildJpql(final AuditQuery query, final boolean count) {
        final StringBuilder sb = new StringBuilder();
        sb.append(count ? "SELECT COUNT(a) FROM AuditEntry a" : "SELECT a FROM AuditEntry a");

        final List<String> predicates = new ArrayList<>();

        if (query.actorId() != null && !query.actorId().isBlank()) {
            predicates.add("a.actor = :actorId");
        }
        if (query.from() != null) {
            predicates.add("a.occurredAt >= :from");
        }
        if (query.to() != null) {
            predicates.add("a.occurredAt <= :to");
        }
        if (query.event() != null && !query.event().isBlank()) {
            predicates.add("a.event = :event");
        }
        if (query.category() != null && !query.category().isBlank()) {
            // Subquery to filter by WorkItem category — no JOIN on the main query
            predicates.add("a.workItemId IN (SELECT w.id FROM WorkItem w WHERE w.category = :category)");
        }

        if (!predicates.isEmpty()) {
            sb.append(" WHERE ").append(String.join(" AND ", predicates));
        }

        if (!count) {
            sb.append(" ORDER BY a.occurredAt DESC");
        }

        return sb.toString();
    }

    private void applyParams(final jakarta.persistence.Query q, final AuditQuery query) {
        if (query.actorId() != null && !query.actorId().isBlank()) {
            q.setParameter("actorId", query.actorId());
        }
        if (query.from() != null) {
            q.setParameter("from", query.from());
        }
        if (query.to() != null) {
            q.setParameter("to", query.to());
        }
        if (query.event() != null && !query.event().isBlank()) {
            q.setParameter("event", query.event());
        }
        if (query.category() != null && !query.category().isBlank()) {
            q.setParameter("category", query.category());
        }
    }
}
