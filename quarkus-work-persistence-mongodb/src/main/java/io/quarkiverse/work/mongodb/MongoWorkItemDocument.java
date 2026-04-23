package io.quarkiverse.work.mongodb;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.bson.codecs.pojo.annotations.BsonId;

import io.quarkiverse.work.runtime.model.DelegationState;
import io.quarkiverse.work.runtime.model.LabelPersistence;
import io.quarkiverse.work.runtime.model.WorkItem;
import io.quarkiverse.work.runtime.model.WorkItemLabel;
import io.quarkiverse.work.runtime.model.WorkItemPriority;
import io.quarkiverse.work.runtime.model.WorkItemStatus;
import io.quarkus.mongodb.panache.PanacheMongoEntityBase;
import io.quarkus.mongodb.panache.common.MongoEntity;

/**
 * MongoDB document representation of a {@link WorkItem}.
 *
 * <p>
 * Stored in the {@code work_items} collection. Converted to and from the domain
 * {@link WorkItem} by {@link MongoWorkItemStore}.
 *
 * <p>
 * Unlike the JPA entity, {@code candidateGroups} and {@code candidateUsers} are stored
 * as arrays (not comma-separated strings), enabling efficient {@code $in} queries.
 */
@MongoEntity(collection = "work_items")
public class MongoWorkItemDocument extends PanacheMongoEntityBase {

    @BsonId
    public String id;

    public String title;
    public String description;
    public String category;
    public String formKey;
    public String status;
    public String priority;
    public String assigneeId;
    public String owner;
    public List<String> candidateGroups = new ArrayList<>();
    public List<String> candidateUsers = new ArrayList<>();
    public String requiredCapabilities;
    public String createdBy;
    public String delegationState;
    public String delegationChain;
    public String priorStatus;
    public String payload;
    public String resolution;
    public Instant claimDeadline;
    public Instant expiresAt;
    public Instant followUpDate;
    public Instant createdAt;
    public Instant updatedAt;
    public Instant assignedAt;
    public Instant startedAt;
    public Instant completedAt;
    public Instant suspendedAt;
    public List<MongoLabel> labels = new ArrayList<>();

    /** Embedded label document. */
    public static class MongoLabel {
        public String path;
        public String persistence;
        public String appliedBy;
    }

    /** Convert a domain {@link WorkItem} to a MongoDB document. */
    public static MongoWorkItemDocument from(final WorkItem wi) {
        final MongoWorkItemDocument doc = new MongoWorkItemDocument();
        doc.id = wi.id != null ? wi.id.toString() : UUID.randomUUID().toString();
        doc.title = wi.title;
        doc.description = wi.description;
        doc.category = wi.category;
        doc.formKey = wi.formKey;
        doc.status = wi.status != null ? wi.status.name() : null;
        doc.priority = wi.priority != null ? wi.priority.name() : null;
        doc.assigneeId = wi.assigneeId;
        doc.owner = wi.owner;
        doc.candidateGroups = splitCsv(wi.candidateGroups);
        doc.candidateUsers = splitCsv(wi.candidateUsers);
        doc.requiredCapabilities = wi.requiredCapabilities;
        doc.createdBy = wi.createdBy;
        doc.delegationState = wi.delegationState != null ? wi.delegationState.name() : null;
        doc.delegationChain = wi.delegationChain;
        doc.priorStatus = wi.priorStatus != null ? wi.priorStatus.name() : null;
        doc.payload = wi.payload;
        doc.resolution = wi.resolution;
        doc.claimDeadline = wi.claimDeadline;
        doc.expiresAt = wi.expiresAt;
        doc.followUpDate = wi.followUpDate;
        doc.createdAt = wi.createdAt;
        doc.updatedAt = wi.updatedAt;
        doc.assignedAt = wi.assignedAt;
        doc.startedAt = wi.startedAt;
        doc.completedAt = wi.completedAt;
        doc.suspendedAt = wi.suspendedAt;
        if (wi.labels != null) {
            doc.labels = wi.labels.stream().map(l -> {
                final MongoLabel ml = new MongoLabel();
                ml.path = l.path;
                ml.persistence = l.persistence != null ? l.persistence.name() : null;
                ml.appliedBy = l.appliedBy;
                return ml;
            }).collect(Collectors.toList());
        }
        return doc;
    }

    /** Convert this document back to a domain {@link WorkItem}. */
    public WorkItem toDomain() {
        final WorkItem wi = new WorkItem();
        wi.id = UUID.fromString(id);
        wi.title = title;
        wi.description = description;
        wi.category = category;
        wi.formKey = formKey;
        wi.status = status != null ? WorkItemStatus.valueOf(status) : null;
        wi.priority = priority != null ? WorkItemPriority.valueOf(priority) : null;
        wi.assigneeId = assigneeId;
        wi.owner = owner;
        wi.candidateGroups = joinCsv(candidateGroups);
        wi.candidateUsers = joinCsv(candidateUsers);
        wi.requiredCapabilities = requiredCapabilities;
        wi.createdBy = createdBy;
        wi.delegationState = delegationState != null ? DelegationState.valueOf(delegationState) : null;
        wi.delegationChain = delegationChain;
        wi.priorStatus = priorStatus != null ? WorkItemStatus.valueOf(priorStatus) : null;
        wi.payload = payload;
        wi.resolution = resolution;
        wi.claimDeadline = claimDeadline;
        wi.expiresAt = expiresAt;
        wi.followUpDate = followUpDate;
        wi.createdAt = createdAt;
        wi.updatedAt = updatedAt;
        wi.assignedAt = assignedAt;
        wi.startedAt = startedAt;
        wi.completedAt = completedAt;
        wi.suspendedAt = suspendedAt;
        wi.labels = labels != null ? labels.stream().map(ml -> {
            final WorkItemLabel label = new WorkItemLabel();
            label.path = ml.path;
            label.persistence = ml.persistence != null ? LabelPersistence.valueOf(ml.persistence) : null;
            label.appliedBy = ml.appliedBy;
            return label;
        }).collect(Collectors.toList()) : new ArrayList<>();
        return wi;
    }

    private static List<String> splitCsv(final String csv) {
        if (csv == null || csv.isBlank()) {
            return new ArrayList<>();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    private static String joinCsv(final List<String> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        return String.join(",", list);
    }
}
