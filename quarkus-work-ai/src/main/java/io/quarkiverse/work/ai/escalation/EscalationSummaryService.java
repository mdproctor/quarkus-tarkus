package io.casehub.work.ai.escalation;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import dev.langchain4j.model.chat.ChatModel;
import io.casehub.work.ai.config.WorkItemsAiConfig;
import io.casehub.work.runtime.model.AuditEntry;
import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.runtime.repository.AuditEntryStore;
import io.casehub.work.runtime.repository.WorkItemStore;

/**
 * Generates and persists LLM-drafted escalation summaries.
 *
 * <p>
 * Called by {@link EscalationSummaryObserver} when a WorkItem fires a
 * {@code EXPIRED} or {@code CLAIM_EXPIRED} lifecycle event. Degrades
 * gracefully when no {@code ChatModel} is configured — a summary is still
 * persisted with a {@code null} text so callers know the event occurred.
 */
@ApplicationScoped
public class EscalationSummaryService {

    private static final Logger LOG = Logger.getLogger(EscalationSummaryService.class);
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneOffset.UTC);

    private final WorkItemStore workItemStore;
    private final AuditEntryStore auditStore;
    private final ChatModel chatModel;
    private final boolean enabled;
    private final int auditLimit;

    @Inject
    public EscalationSummaryService(
            final WorkItemStore workItemStore,
            final AuditEntryStore auditStore,
            final Instance<ChatModel> chatModelInstance,
            final WorkItemsAiConfig config) {
        this.workItemStore = workItemStore;
        this.auditStore = auditStore;
        this.chatModel = chatModelInstance.isResolvable() ? chatModelInstance.get() : null;
        this.enabled = config.escalationSummary().enabled();
        this.auditLimit = config.escalationSummary().auditLimit();
    }

    /** Test constructor — bypasses CDI and config. */
    EscalationSummaryService(final WorkItemStore workItemStore, final AuditEntryStore auditStore,
            final ChatModel chatModel, final boolean enabled, final int auditLimit) {
        this.workItemStore = workItemStore;
        this.auditStore = auditStore;
        this.chatModel = chatModel;
        this.enabled = enabled;
        this.auditLimit = auditLimit;
    }

    /**
     * Build an {@link EscalationSummary} for {@code workItemId} without persisting it.
     * The caller is responsible for persisting within a transaction.
     *
     * <p>
     * Always returns a record — even when no model is configured — so callers can
     * persist a trace of the escalation event without a text summary.
     *
     * @param workItemId the escalating WorkItem
     * @param eventType {@code "EXPIRED"} or {@code "CLAIM_EXPIRED"}
     * @return an unpersisted {@link EscalationSummary}
     */
    public EscalationSummary buildSummary(final java.util.UUID workItemId, final String eventType) {
        final EscalationSummary record = new EscalationSummary();
        record.workItemId = workItemId;
        record.eventType = eventType;

        if (!enabled) {
            LOG.debugf("Escalation summarisation disabled — skipping model call for %s", workItemId);
            return record;
        }

        if (chatModel == null) {
            LOG.warnf("No ChatModel available — escalation summary text will be null for %s",
                    workItemId);
            return record;
        }

        final WorkItem workItem = workItemStore.get(workItemId).orElse(null);
        if (workItem == null) {
            LOG.warnf("WorkItem %s not found for escalation summary", workItemId);
            return record;
        }

        try {
            final List<AuditEntry> audit = auditStore.findByWorkItemId(workItemId);
            record.summary = chatModel.chat(buildPrompt(workItem, audit, eventType));
        } catch (final Exception e) {
            LOG.warnf("ChatModel failed during escalation summarisation for %s: %s",
                    workItemId, e.getMessage());
        }

        return record;
    }

    private String buildPrompt(final WorkItem wi, final List<AuditEntry> allAudit,
            final String eventType) {
        final List<AuditEntry> recent = allAudit.stream()
                .sorted((a, b) -> b.occurredAt.compareTo(a.occurredAt))
                .limit(auditLimit)
                .toList();

        final String escalationReason = "EXPIRED".equals(eventType)
                ? "completion deadline has passed without the work item being resolved"
                : "claim deadline has passed without anyone picking up the work item";

        final StringBuilder sb = new StringBuilder();
        sb.append("You are an escalation assistant. Write a concise briefing (3-5 sentences) ")
                .append("for the escalation target about the following work item.\n\n");

        sb.append("Work item:\n");
        sb.append("Title: ").append(wi.title).append("\n");
        if (wi.description != null && !wi.description.isBlank()) {
            sb.append("Description: ").append(wi.description).append("\n");
        }
        if (wi.category != null) {
            sb.append("Category: ").append(wi.category).append("\n");
        }
        sb.append("Escalation reason: ").append(escalationReason).append("\n\n");

        if (!recent.isEmpty()) {
            sb.append("Recent history (most recent first):\n");
            for (final AuditEntry entry : recent) {
                sb.append("- [").append(TIMESTAMP.format(entry.occurredAt)).append("] ")
                        .append(entry.event)
                        .append(" by ").append(entry.actor != null ? entry.actor : "system");
                if (entry.detail != null && !entry.detail.isBlank()) {
                    sb.append(": ").append(entry.detail);
                }
                sb.append("\n");
            }
            sb.append("\n");
        }

        sb.append("Write the briefing in plain text. Do not use JSON or markdown headers.");
        return sb.toString();
    }
}
