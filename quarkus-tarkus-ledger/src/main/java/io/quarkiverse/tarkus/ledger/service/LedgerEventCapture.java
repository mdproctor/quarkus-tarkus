package io.quarkiverse.tarkus.ledger.service;

import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import io.quarkiverse.tarkus.ledger.config.LedgerConfig;
import io.quarkiverse.tarkus.ledger.model.ActorType;
import io.quarkiverse.tarkus.ledger.model.LedgerEntry;
import io.quarkiverse.tarkus.ledger.model.LedgerEntryType;
import io.quarkiverse.tarkus.ledger.repository.LedgerEntryRepository;
import io.quarkiverse.tarkus.runtime.event.WorkItemLifecycleEvent;
import io.quarkiverse.tarkus.runtime.model.WorkItem;
import io.quarkiverse.tarkus.runtime.repository.WorkItemRepository;

/**
 * CDI observer that writes a {@link LedgerEntry} for every WorkItem lifecycle transition.
 *
 * <p>
 * This bean is the sole integration point between the core Tarkus extension and the ledger
 * module. The core fires {@link WorkItemLifecycleEvent} CDI events on every transition; this
 * observer captures them and appends an immutable record to the ledger. The core has no
 * knowledge of this observer — if the ledger module is absent, events fire into the void.
 *
 * <p>
 * The observer runs in the same transaction as the lifecycle event delivery. If the
 * enclosing transaction rolls back, the ledger entry is not written — preserving consistency.
 */
@ApplicationScoped
public class LedgerEventCapture {

    @Inject
    LedgerEntryRepository ledgerRepo;

    @Inject
    WorkItemRepository workItemRepo;

    @Inject
    LedgerConfig config;

    /**
     * Observe a WorkItem lifecycle event and write the corresponding ledger entry.
     *
     * @param event the lifecycle event fired by {@code WorkItemService}
     */
    @Transactional
    void onWorkItemEvent(@Observes final WorkItemLifecycleEvent event) {
        if (!config.enabled()) {
            return;
        }

        // Load the WorkItem for the decisionContext snapshot
        final Optional<WorkItem> workItemOpt = workItemRepo.findById(event.workItemId());

        // Determine the next sequence number in this WorkItem's ledger
        final int seq = ledgerRepo.findLatestByWorkItemId(event.workItemId())
                .map(e -> e.sequenceNumber + 1)
                .orElse(1);

        // Retrieve the previous digest for hash chaining
        final String previousHash = ledgerRepo.findLatestByWorkItemId(event.workItemId())
                .map(e -> e.digest)
                .orElse(null);

        final LedgerEntry entry = new LedgerEntry();
        entry.workItemId = event.workItemId();
        entry.sequenceNumber = seq;
        entry.entryType = LedgerEntryType.EVENT;
        entry.commandType = deriveCommandType(event.type());
        entry.eventType = deriveEventType(event.type());
        entry.actorId = event.actor();
        entry.actorType = ActorType.HUMAN; // default; agents identified by actorId prefix convention
        entry.actorRole = deriveActorRole(event.type());
        entry.rationale = event.rationale();
        entry.planRef = event.planRef();
        entry.detail = event.detail();
        entry.correlationId = null; // future: from OTEL context
        // Set occurredAt explicitly with millis precision before hash computation —
        // @PrePersist sets it too late; DB truncates to millis so canonical form must match
        entry.occurredAt = java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);

        // Decision context snapshot
        if (config.decisionContext().enabled() && workItemOpt.isPresent()) {
            entry.decisionContext = buildDecisionContext(workItemOpt.get());
        }

        // Hash chain
        if (config.hashChain().enabled()) {
            entry.previousHash = previousHash;
            entry.digest = LedgerHashChain.compute(previousHash, entry);
        }

        ledgerRepo.save(entry);
    }

    /**
     * Snapshot the WorkItem's current state as a compact JSON string.
     *
     * @param wi the WorkItem to snapshot
     * @return a JSON object string containing status, priority, assigneeId, and expiresAt
     */
    private String buildDecisionContext(final WorkItem wi) {
        return String.format(
                "{\"status\":\"%s\",\"priority\":\"%s\",\"assigneeId\":%s,\"expiresAt\":%s}",
                wi.status,
                wi.priority,
                wi.assigneeId != null ? "\"" + wi.assigneeId + "\"" : "null",
                wi.expiresAt != null ? "\"" + wi.expiresAt + "\"" : "null");
    }

    /**
     * Derive the command type (actor intent) from the CloudEvents-style event type string.
     *
     * @param eventType the fully-qualified event type, e.g. {@code "io.quarkiverse.tarkus.workitem.completed"}
     * @return a PascalCase command name, or {@code null} if not mappable
     */
    private String deriveCommandType(final String eventType) {
        if (eventType == null) {
            return null;
        }
        final String[] parts = eventType.split("\\.");
        final String last = parts[parts.length - 1];
        return switch (last) {
            case "created" -> "CreateWorkItem";
            case "assigned" -> "ClaimWorkItem";
            case "started" -> "StartWorkItem";
            case "completed" -> "CompleteWorkItem";
            case "rejected" -> "RejectWorkItem";
            case "delegated" -> "DelegateWorkItem";
            case "released" -> "ReleaseWorkItem";
            case "suspended" -> "SuspendWorkItem";
            case "resumed" -> "ResumeWorkItem";
            case "cancelled" -> "CancelWorkItem";
            case "expired" -> "ExpireWorkItem";
            case "escalated" -> "EscalateWorkItem";
            default -> null;
        };
    }

    /**
     * Derive the event type (observable fact) from the CloudEvents-style event type string.
     *
     * @param eventType the fully-qualified event type, e.g. {@code "io.quarkiverse.tarkus.workitem.completed"}
     * @return a PascalCase event name such as {@code "WorkItemCompleted"}, or {@code null} if input is null
     */
    private String deriveEventType(final String eventType) {
        if (eventType == null) {
            return null;
        }
        final String[] parts = eventType.split("\\.");
        final String last = parts[parts.length - 1];
        final String capitalised = last.substring(0, 1).toUpperCase() + last.substring(1);
        return "WorkItem" + capitalised;
    }

    /**
     * Derive the functional role of the actor from the CloudEvents-style event type string.
     *
     * @param eventType the fully-qualified event type
     * @return a role label such as {@code "Resolver"} or {@code "Delegator"}, or {@code null} if input is null
     */
    private String deriveActorRole(final String eventType) {
        if (eventType == null) {
            return null;
        }
        final String[] parts = eventType.split("\\.");
        final String last = parts[parts.length - 1];
        return switch (last) {
            case "created" -> "Initiator";
            case "assigned" -> "Claimant";
            case "completed", "rejected" -> "Resolver";
            case "delegated" -> "Delegator";
            case "cancelled" -> "Administrator";
            case "expired", "escalated" -> "System";
            default -> "Assignee";
        };
    }
}
