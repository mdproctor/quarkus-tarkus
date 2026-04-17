package io.quarkiverse.workitems.runtime.repository.jpa;

import java.util.List;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.workitems.runtime.model.AuditEntry;
import io.quarkiverse.workitems.runtime.repository.AuditEntryStore;

/**
 * Default JPA/Panache implementation of {@link AuditEntryStore}.
 */
@ApplicationScoped
public class JpaAuditEntryStore implements AuditEntryStore {

    @Override
    public void append(final AuditEntry entry) {
        entry.persist();
    }

    @Override
    public List<AuditEntry> findByWorkItemId(final UUID workItemId) {
        return AuditEntry.list("workItemId = ?1 ORDER BY occurredAt ASC", workItemId);
    }
}
