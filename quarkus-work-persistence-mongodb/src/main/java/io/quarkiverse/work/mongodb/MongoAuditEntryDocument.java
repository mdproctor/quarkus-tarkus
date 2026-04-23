package io.quarkiverse.work.mongodb;

import java.time.Instant;
import java.util.UUID;

import org.bson.codecs.pojo.annotations.BsonId;

import io.quarkiverse.work.runtime.model.AuditEntry;
import io.quarkus.mongodb.panache.PanacheMongoEntityBase;
import io.quarkus.mongodb.panache.common.MongoEntity;

/**
 * MongoDB document representation of an {@link AuditEntry}.
 *
 * <p>
 * Stored in the {@code audit_entries} collection. Append-only: documents are
 * never updated or deleted. Converted to and from the domain {@link AuditEntry}
 * by {@link MongoAuditEntryStore}.
 */
@MongoEntity(collection = "audit_entries")
public class MongoAuditEntryDocument extends PanacheMongoEntityBase {

    @BsonId
    public String id;

    public String workItemId;
    public String event;
    public String actor;
    public String detail;
    public Instant occurredAt;

    /** Convert a domain {@link AuditEntry} to a MongoDB document. */
    public static MongoAuditEntryDocument from(final AuditEntry entry) {
        final MongoAuditEntryDocument doc = new MongoAuditEntryDocument();
        doc.id = entry.id != null ? entry.id.toString() : UUID.randomUUID().toString();
        doc.workItemId = entry.workItemId != null ? entry.workItemId.toString() : null;
        doc.event = entry.event;
        doc.actor = entry.actor;
        doc.detail = entry.detail;
        doc.occurredAt = entry.occurredAt != null ? entry.occurredAt : Instant.now();
        return doc;
    }

    /** Convert this document back to a domain {@link AuditEntry}. */
    public AuditEntry toDomain() {
        final AuditEntry entry = new AuditEntry();
        entry.id = UUID.fromString(id);
        entry.workItemId = workItemId != null ? UUID.fromString(workItemId) : null;
        entry.event = event;
        entry.actor = actor;
        entry.detail = detail;
        entry.occurredAt = occurredAt;
        return entry;
    }
}
