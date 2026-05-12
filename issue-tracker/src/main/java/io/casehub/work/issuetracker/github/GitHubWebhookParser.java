package io.casehub.work.issuetracker.github;

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
 * Parses raw GitHub Issues webhook payloads into normalised {@link WebhookEvent} records.
 *
 * <p>Managed labels ({@code priority:*}, {@code status:*}, {@code category:*}) are filtered
 * on inbound label events — they are owned by the outbound sync and must not echo back.
 */
@ApplicationScoped
public class GitHubWebhookParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> MANAGED_LABEL_PREFIXES = Set.of("priority:", "status:", "category:");

    /**
     * Parse a raw GitHub Issues webhook payload.
     *
     * @param headers the HTTP request headers (unused — GitHub sends event type in a header
     *        but we derive it from the {@code action} field instead)
     * @param body the raw request body
     * @return the normalised event, or {@code null} if the action is not handled
     */
    public WebhookEvent parse(final Map<String, String> headers, final String body) {
        try {
            final JsonNode root = MAPPER.readTree(body);
            final String action = root.path("action").asText("");
            final JsonNode issue = root.path("issue");
            final String repo = root.path("repository").path("full_name").asText("");
            final String number = String.valueOf(issue.path("number").asInt());
            final String externalRef = repo + "#" + number;
            final String actor = root.path("sender").path("login").asText("unknown");

            return switch (action) {
                case "closed" -> parseClosed(externalRef, actor, issue);
                case "assigned" -> parseAssigned(externalRef, actor, root);
                case "unassigned" -> new WebhookEvent("github", externalRef, WebhookEventKind.UNASSIGNED,
                        actor, null, null, null, null, null, null);
                case "edited" -> parseEdited(externalRef, actor, issue, root);
                case "labeled" -> parseLabeled(externalRef, actor, root);
                case "unlabeled" -> parseLabelRemoved(externalRef, actor, root);
                default -> null;
            };
        } catch (final Exception e) {
            throw new IssueTrackerException("Failed to parse GitHub webhook payload: " + e.getMessage(), e);
        }
    }

    private WebhookEvent parseClosed(final String externalRef, final String actor, final JsonNode issue) {
        final String stateReason = issue.path("state_reason").asText(null);
        final NormativeResolution resolution = "not_planned".equals(stateReason)
                ? NormativeResolution.DECLINE
                : NormativeResolution.DONE;
        return new WebhookEvent("github", externalRef, WebhookEventKind.CLOSED,
                actor, resolution, null, null, null, null, null);
    }

    private WebhookEvent parseAssigned(final String externalRef, final String actor, final JsonNode root) {
        final String assignee = root.path("assignee").path("login").asText(null);
        return new WebhookEvent("github", externalRef, WebhookEventKind.ASSIGNED,
                actor, null, null, null, null, null, assignee);
    }

    private WebhookEvent parseEdited(
            final String externalRef, final String actor, final JsonNode issue, final JsonNode root) {
        final JsonNode changes = root.path("changes");
        if (changes.has("body")) {
            return new WebhookEvent("github", externalRef, WebhookEventKind.DESCRIPTION_CHANGED,
                    actor, null, null, null, null, issue.path("body").asText(null), null);
        }
        if (changes.has("title")) {
            return new WebhookEvent("github", externalRef, WebhookEventKind.TITLE_CHANGED,
                    actor, null, null, null, issue.path("title").asText(null), null, null);
        }
        return null;
    }

    private WebhookEvent parseLabeled(final String externalRef, final String actor, final JsonNode root) {
        final String labelName = root.path("label").path("name").asText("");
        if (labelName.startsWith("priority:")) {
            final WorkItemPriority priority = parsePriorityLabel(labelName);
            if (priority == null) return null;
            return new WebhookEvent("github", externalRef, WebhookEventKind.PRIORITY_CHANGED,
                    actor, null, priority, null, null, null, null);
        }
        if (isManagedLabel(labelName)) return null;
        return new WebhookEvent("github", externalRef, WebhookEventKind.LABEL_ADDED,
                actor, null, null, labelName, null, null, null);
    }

    private WebhookEvent parseLabelRemoved(final String externalRef, final String actor, final JsonNode root) {
        final String labelName = root.path("label").path("name").asText("");
        if (isManagedLabel(labelName)) return null;
        return new WebhookEvent("github", externalRef, WebhookEventKind.LABEL_REMOVED,
                actor, null, null, labelName, null, null, null);
    }

    private boolean isManagedLabel(final String name) {
        return MANAGED_LABEL_PREFIXES.stream().anyMatch(name::startsWith);
    }

    private WorkItemPriority parsePriorityLabel(final String name) {
        return switch (name) {
            case "priority:urgent" -> WorkItemPriority.URGENT;
            case "priority:high" -> WorkItemPriority.HIGH;
            case "priority:medium" -> WorkItemPriority.MEDIUM;
            case "priority:low" -> WorkItemPriority.LOW;
            default -> null;
        };
    }
}
