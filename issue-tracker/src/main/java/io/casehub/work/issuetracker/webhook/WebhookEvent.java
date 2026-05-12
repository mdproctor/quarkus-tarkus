package io.casehub.work.issuetracker.webhook;

import io.casehub.work.api.NormativeResolution;
import io.casehub.work.runtime.model.WorkItemPriority;

/**
 * Normalised inbound event from an external issue tracker.
 *
 * <p>Tracker-specific vocabulary (GitHub actions, Jira changelog fields) is
 * translated into this record by the {@link io.casehub.work.issuetracker.spi.IssueTrackerProvider}.
 * The {@link WebhookEventHandler} then applies the appropriate WorkItem transition
 * without knowing which tracker sent it.
 *
 * <p>Unused fields for a given {@link WebhookEventKind} are {@code null}.
 */
public record WebhookEvent(
        /** The tracker type: {@code "github"} or {@code "jira"}. */
        String trackerType,
        /** Tracker-specific issue reference, e.g. {@code "owner/repo#42"} or {@code "PROJ-1234"}. */
        String externalRef,
        /** The kind of event. */
        WebhookEventKind eventKind,
        /** The actor who triggered the event (GitHub login or Jira display name). */
        String actor,
        /** Resolution type — only set when {@code eventKind == CLOSED}. */
        NormativeResolution normativeResolution,
        /** New priority — only set when {@code eventKind == PRIORITY_CHANGED}. */
        WorkItemPriority newPriority,
        /** Label value — only set when {@code eventKind == LABEL_ADDED or LABEL_REMOVED}. */
        String labelValue,
        /** New title — only set when {@code eventKind == TITLE_CHANGED}. */
        String newTitle,
        /** New description — only set when {@code eventKind == DESCRIPTION_CHANGED}. */
        String newDescription,
        /** New assignee ID — only set when {@code eventKind == ASSIGNED}. */
        String newAssignee) {
}
