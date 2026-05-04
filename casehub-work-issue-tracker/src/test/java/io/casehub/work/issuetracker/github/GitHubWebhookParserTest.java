package io.casehub.work.issuetracker.github;

import io.casehub.work.api.NormativeResolution;
import io.casehub.work.issuetracker.webhook.WebhookEvent;
import io.casehub.work.issuetracker.webhook.WebhookEventKind;
import io.casehub.work.runtime.model.WorkItemPriority;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GitHubWebhookParserTest {

    private final GitHubWebhookParser parser = new GitHubWebhookParser();

    private String fixture(final String name) throws IOException {
        try (var stream = getClass().getResourceAsStream("/fixtures/github/" + name)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void closedCompleted_parsesDone() throws IOException {
        final WebhookEvent event = parser.parse(Map.of(), fixture("issue-closed-completed.json"));

        assertThat(event).isNotNull();
        assertThat(event.eventKind()).isEqualTo(WebhookEventKind.CLOSED);
        assertThat(event.normativeResolution()).isEqualTo(NormativeResolution.DONE);
        assertThat(event.externalRef()).isEqualTo("owner/repo#42");
        assertThat(event.actor()).isEqualTo("alice");
        assertThat(event.trackerType()).isEqualTo("github");
    }

    @Test
    void closedNotPlanned_parsesDecline() throws IOException {
        final WebhookEvent event = parser.parse(Map.of(), fixture("issue-closed-not-planned.json"));

        assertThat(event.normativeResolution()).isEqualTo(NormativeResolution.DECLINE);
    }

    @Test
    void closedLegacy_nullStateReason_parsesDone() throws IOException {
        final WebhookEvent event = parser.parse(Map.of(), fixture("issue-closed-legacy.json"));

        assertThat(event.normativeResolution()).isEqualTo(NormativeResolution.DONE);
    }

    @Test
    void assigned_parsesAssigned() throws IOException {
        final WebhookEvent event = parser.parse(Map.of(), fixture("issue-assigned.json"));

        assertThat(event.eventKind()).isEqualTo(WebhookEventKind.ASSIGNED);
        assertThat(event.newAssignee()).isEqualTo("bob");
        assertThat(event.actor()).isEqualTo("alice");
    }

    @Test
    void unassigned_parsesUnassigned() throws IOException {
        final WebhookEvent event = parser.parse(Map.of(), fixture("issue-unassigned.json"));

        assertThat(event.eventKind()).isEqualTo(WebhookEventKind.UNASSIGNED);
        assertThat(event.actor()).isEqualTo("alice");
    }

    @Test
    void editedBody_parsesDescriptionChanged() throws IOException {
        final WebhookEvent event = parser.parse(Map.of(), fixture("issue-edited-body.json"));

        assertThat(event.eventKind()).isEqualTo(WebhookEventKind.DESCRIPTION_CHANGED);
        assertThat(event.newDescription()).isEqualTo("Updated description text.");
    }

    @Test
    void editedTitle_parsesTitleChanged() throws IOException {
        final WebhookEvent event = parser.parse(Map.of(), fixture("issue-edited-title.json"));

        assertThat(event.eventKind()).isEqualTo(WebhookEventKind.TITLE_CHANGED);
        assertThat(event.newTitle()).isEqualTo("Updated title");
    }

    @Test
    void labeledPriority_parsesPriorityChanged() throws IOException {
        final WebhookEvent event = parser.parse(Map.of(), fixture("issue-labeled-priority.json"));

        assertThat(event.eventKind()).isEqualTo(WebhookEventKind.PRIORITY_CHANGED);
        assertThat(event.newPriority()).isEqualTo(WorkItemPriority.HIGH);
    }

    @Test
    void labeledUser_parsesLabelAdded() throws IOException {
        final WebhookEvent event = parser.parse(Map.of(), fixture("issue-labeled-user.json"));

        assertThat(event.eventKind()).isEqualTo(WebhookEventKind.LABEL_ADDED);
        assertThat(event.labelValue()).isEqualTo("legal/nda");
    }

    @Test
    void unlabeled_parsesLabelRemoved() throws IOException {
        final WebhookEvent event = parser.parse(Map.of(), fixture("issue-unlabeled.json"));

        assertThat(event.eventKind()).isEqualTo(WebhookEventKind.LABEL_REMOVED);
        assertThat(event.labelValue()).isEqualTo("legal/nda");
    }

    @Test
    void reopened_returnsNull() throws Exception {
        final String body = """
            {"action":"reopened","issue":{"number":42,"title":"T","body":"B",
             "state":"open","state_reason":null,"html_url":"https://github.com/o/r/issues/42",
             "assignee":null,"labels":[]},"repository":{"full_name":"o/r"},
             "sender":{"login":"alice"}}
            """;
        assertThat(parser.parse(Map.of(), body)).isNull();
    }
}
