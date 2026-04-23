package io.quarkiverse.work.mongodb;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import org.bson.Document;

import io.quarkiverse.work.runtime.model.WorkItem;
import io.quarkiverse.work.runtime.repository.WorkItemQuery;
import io.quarkiverse.work.runtime.repository.WorkItemStore;

/**
 * MongoDB implementation of {@link WorkItemStore}.
 *
 * <p>
 * Selected by CDI over the default {@code JpaWorkItemStore} when this module is on
 * the classpath. Translates {@link WorkItemQuery} to MongoDB {@link Document} filters:
 * assignment fields use {@code $or} logic; all other filters are combined with
 * {@code $and}. Label patterns use {@code $regex} on the embedded {@code labels.path}
 * array field.
 *
 * <p>
 * {@code candidateGroups} and {@code candidateUsers} are stored as arrays in MongoDB
 * (split from the JPA comma-separated string on write, rejoined on read), enabling
 * efficient {@code $in} and element-match queries.
 */
@ApplicationScoped
@Alternative
@Priority(1)
public class MongoWorkItemStore implements WorkItemStore {

    @Override
    public WorkItem put(final WorkItem workItem) {
        if (workItem.id == null) {
            workItem.id = UUID.randomUUID();
        }
        final Instant now = Instant.now();
        if (workItem.createdAt == null) {
            workItem.createdAt = now;
        }
        workItem.updatedAt = now;

        MongoWorkItemDocument.from(workItem).persistOrUpdate();
        return workItem;
    }

    @Override
    public Optional<WorkItem> get(final UUID id) {
        final MongoWorkItemDocument doc = MongoWorkItemDocument.findById(id.toString());
        return Optional.ofNullable(doc).map(MongoWorkItemDocument::toDomain);
    }

    @Override
    public List<WorkItem> scan(final WorkItemQuery query) {
        final Document filter = buildFilter(query);
        final List<MongoWorkItemDocument> docs = filter.isEmpty()
                ? MongoWorkItemDocument.listAll()
                : MongoWorkItemDocument.<MongoWorkItemDocument> find(filter).list();
        return docs.stream().map(MongoWorkItemDocument::toDomain).toList();
    }

    // ── Filter builder ────────────────────────────────────────────────────────

    private Document buildFilter(final WorkItemQuery q) {
        final List<Document> ands = new ArrayList<>();

        // Assignment — OR logic across three dimensions
        final boolean hasAssigneeId = q.assigneeId() != null;
        final boolean hasCandidateGroups = q.candidateGroups() != null && !q.candidateGroups().isEmpty();
        final boolean hasCandidateUserId = q.candidateUserId() != null;

        if (hasAssigneeId || hasCandidateGroups || hasCandidateUserId) {
            final List<Document> ors = new ArrayList<>();
            if (hasAssigneeId) {
                ors.add(new Document("assigneeId", q.assigneeId()));
                // array contains q.assigneeId()
                ors.add(new Document("candidateUsers", q.assigneeId()));
            }
            if (hasCandidateUserId && !hasAssigneeId) {
                ors.add(new Document("candidateUsers", q.candidateUserId()));
            }
            if (hasCandidateGroups) {
                // array intersects q.candidateGroups()
                ors.add(new Document("candidateGroups", new Document("$in", q.candidateGroups())));
            }
            ands.add(new Document("$or", ors));
        }

        // Status (exact)
        if (q.status() != null) {
            ands.add(new Document("status", q.status().name()));
        }

        // StatusIn
        if (q.statusIn() != null && !q.statusIn().isEmpty()) {
            ands.add(new Document("status",
                    new Document("$in", q.statusIn().stream().map(Enum::name).toList())));
        }

        // Priority
        if (q.priority() != null) {
            ands.add(new Document("priority", q.priority().name()));
        }

        // Category
        if (q.category() != null) {
            ands.add(new Document("category", q.category()));
        }

        // FollowUpBefore — field must exist (not null) and be <= threshold
        if (q.followUpBefore() != null) {
            ands.add(new Document("followUpDate",
                    new Document("$ne", null).append("$lte", toDate(q.followUpBefore()))));
        }

        // ExpiresAtOrBefore — field must exist (not null) and be <= threshold
        if (q.expiresAtOrBefore() != null) {
            ands.add(new Document("expiresAt",
                    new Document("$ne", null).append("$lte", toDate(q.expiresAtOrBefore()))));
        }

        // ClaimDeadlineOrBefore — field must exist (not null) and be <= threshold
        if (q.claimDeadlineOrBefore() != null) {
            ands.add(new Document("claimDeadline",
                    new Document("$ne", null).append("$lte", toDate(q.claimDeadlineOrBefore()))));
        }

        // Label pattern — regex on embedded array field
        if (q.labelPattern() != null) {
            ands.add(buildLabelFilter(q.labelPattern()));
        }

        if (ands.isEmpty()) {
            return new Document();
        }
        if (ands.size() == 1) {
            return ands.get(0);
        }
        return new Document("$and", ands);
    }

    private Document buildLabelFilter(final String pattern) {
        if (pattern.endsWith("/**")) {
            final String prefix = pattern.substring(0, pattern.length() - 3) + "/";
            return new Document("labels.path", new Document("$regex", "^" + prefix));
        }
        if (pattern.endsWith("/*")) {
            final String prefix = pattern.substring(0, pattern.length() - 2) + "/";
            return new Document("labels.path", new Document("$regex", "^" + prefix + "[^/]+$"));
        }
        return new Document("labels.path", pattern);
    }

    /** Convert Instant to java.util.Date for BSON date comparison in raw Document queries. */
    private static Date toDate(final Instant instant) {
        return Date.from(instant);
    }
}
