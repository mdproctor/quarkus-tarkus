package io.quarkiverse.workitems.mongodb;

import java.util.List;
import java.util.UUID;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import org.bson.Document;

import io.quarkiverse.workitems.runtime.model.AuditEntry;
import io.quarkiverse.workitems.runtime.repository.AuditEntryStore;

/**
 * MongoDB implementation of {@link AuditEntryStore}.
 *
 * <p>
 * Selected by CDI over the default {@code JpaAuditEntryStore} when this module is on
 * the classpath. Audit entries are append-only: documents are never updated or deleted.
 */
@ApplicationScoped
@Alternative
@Priority(1)
public class MongoAuditEntryStore implements AuditEntryStore {

    @Override
    public void append(final AuditEntry entry) {
        MongoAuditEntryDocument.from(entry).persist();
    }

    @Override
    public List<AuditEntry> findByWorkItemId(final UUID workItemId) {
        return MongoAuditEntryDocument
                .<MongoAuditEntryDocument> find(new Document("workItemId", workItemId.toString()))
                .list()
                .stream()
                .map(MongoAuditEntryDocument::toDomain)
                .toList();
    }
}
