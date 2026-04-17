package io.quarkiverse.workitems.runtime.repository;

import java.util.List;
import java.util.UUID;

import io.quarkiverse.workitems.runtime.model.AuditEntry;

/**
 * Append-only store SPI for {@link AuditEntry} records.
 * Replaces {@code AuditEntryRepository} — aligned with KV store terminology.
 */
public interface AuditEntryStore {

    /**
     * Append an audit entry to the store.
     *
     * @param entry the entry to append; must not be {@code null}
     */
    void append(AuditEntry entry);

    /**
     * Return all audit entries for the given WorkItem, in chronological order.
     *
     * @param workItemId the WorkItem primary key
     * @return chronological list of audit entries; may be empty, never null
     */
    List<AuditEntry> findByWorkItemId(UUID workItemId);
}
