package io.casehub.work.issuetracker.jira;

import java.util.Map;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.casehub.work.api.NormativeResolution;
import io.casehub.work.issuetracker.spi.IssueTrackerException;
import io.casehub.work.issuetracker.webhook.WebhookEvent;
import io.casehub.work.issuetracker.webhook.WebhookEventKind;
import io.casehub.work.runtime.model.WorkItemPriority;

/**
 * Parses raw Jira webhook payloads ({@code jira:issue_updated}) into normalised
 * {@link WebhookEvent} records.
 *
 * <p>Jira sends a single {@code jira:issue_updated} event for all field changes.
 * The {@code changelog.items} array identifies what changed. This parser inspects
 * the changelog items in order and returns the first handled event kind found.
 * Priority: resolution > assignee > priority > description > summary.
 *
 * <p>Returns {@code null} for non-{@code jira:issue_updated} events or payloads
 * with no handled changelog items.
 */
@ApplicationScoped
public class JiraWebhookParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> DECLINE_RESOLUTIONS = Set.of(
            "Won't Do", "Won't Fix", "Duplicate");
    private static final Set<String> FAILURE_RESOLUTIONS = Set.of(
            "Cannot Reproduce", "Incomplete");

    /**
     * Parse a raw Jira webhook payload.
     *
     * @param headers the HTTP request headers (unused — Jira does not send event type headers)
     * @param body the raw request body
     * @return the normalised event, or {@code null} if no handled change is present
     */
    public WebhookEvent parse(final Map<String, String> headers, final String body) {
        try {
            final JsonNode root = MAPPER.readTree(body);
            final String webhookEvent = root.path("webhookEvent").asText("");
            if (!"jira:issue_updated".equals(webhookEvent)) return null;

            final String issueKey = root.path("issue").path("key").asText(null);
            if (issueKey == null) return null;

            final String actor = root.path("user").path("displayName").asText("unknown");
            final JsonNode items = root.path("changelog").path("items");

            for (final JsonNode item : items) {
                final String field = item.path("field").asText("");
                final WebhookEvent event = parseItem(field, item, issueKey, actor, root);
                if (event != null) return event;
            }
            return null;

        } catch (final Exception e) {
            throw new IssueTrackerException("Failed to parse Jira webhook payload: " + e.getMessage(), e);
        }
    }

    private WebhookEvent parseItem(
            final String field, final JsonNode item, final String issueKey,
            final String actor, final JsonNode root) {
        return switch (field) {
            case "resolution" -> parseResolution(item, issueKey, actor);
            case "assignee" -> parseAssignee(item, issueKey, actor);
            case "priority" -> parsePriority(issueKey, actor, root);
            case "description" -> new WebhookEvent("jira", issueKey, WebhookEventKind.DESCRIPTION_CHANGED,
                    actor, null, null, null, null,
                    root.path("issue").path("fields").path("description").asText(null), null);
            case "summary" -> new WebhookEvent("jira", issueKey, WebhookEventKind.TITLE_CHANGED,
                    actor, null, null, null,
                    root.path("issue").path("fields").path("summary").asText(null), null, null);
            default -> null;
        };
    }

    private WebhookEvent parseResolution(
            final JsonNode item, final String issueKey, final String actor) {
        final String resolution = item.path("toString").asText(null);
        if (resolution == null) return null;
        return new WebhookEvent("jira", issueKey, WebhookEventKind.CLOSED,
                actor, toNormativeResolution(resolution), null, null, null, null, null);
    }

    private WebhookEvent parseAssignee(
            final JsonNode item, final String issueKey, final String actor) {
        final String toAccountId = item.path("to").asText(null);
        if (toAccountId == null || toAccountId.isBlank()) {
            return new WebhookEvent("jira", issueKey, WebhookEventKind.UNASSIGNED,
                    actor, null, null, null, null, null, null);
        }
        return new WebhookEvent("jira", issueKey, WebhookEventKind.ASSIGNED,
                actor, null, null, null, null, null, toAccountId);
    }

    private WebhookEvent parsePriority(
            final String issueKey, final String actor, final JsonNode root) {
        final String priorityName = root.path("issue").path("fields").path("priority").path("name").asText("");
        final WorkItemPriority priority = toWorkItemPriority(priorityName);
        if (priority == null) return null;
        return new WebhookEvent("jira", issueKey, WebhookEventKind.PRIORITY_CHANGED,
                actor, null, priority, null, null, null, null);
    }

    private NormativeResolution toNormativeResolution(final String resolution) {
        if (DECLINE_RESOLUTIONS.contains(resolution)) return NormativeResolution.DECLINE;
        if (FAILURE_RESOLUTIONS.contains(resolution)) return NormativeResolution.FAILURE;
        return NormativeResolution.DONE;
    }

    private WorkItemPriority toWorkItemPriority(final String jiraPriority) {
        return switch (jiraPriority) {
            case "Highest" -> WorkItemPriority.URGENT;
            case "High" -> WorkItemPriority.HIGH;
            case "Medium" -> WorkItemPriority.MEDIUM;
            case "Low", "Lowest" -> WorkItemPriority.LOW;
            default -> null;
        };
    }
}
