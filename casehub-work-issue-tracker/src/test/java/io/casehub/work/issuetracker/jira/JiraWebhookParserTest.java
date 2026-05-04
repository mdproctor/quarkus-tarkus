package io.casehub.work.issuetracker.jira;

import io.casehub.work.api.NormativeResolution;
import io.casehub.work.issuetracker.webhook.WebhookEvent;
import io.casehub.work.issuetracker.webhook.WebhookEventKind;
import io.casehub.work.runtime.model.WorkItemPriority;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JiraWebhookParserTest {

    private final JiraWebhookParser parser = new JiraWebhookParser();

    private String fixture(final String name) throws IOException {
        try (var stream = getClass().getResourceAsStream("/fixtures/jira/" + name)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void resolvedDone_parsesDone() throws IOException {
        final WebhookEvent event = parser.parse(Map.of(), fixture("issue-resolved-done.json"));

        assertThat(event).isNotNull();
        assertThat(event.eventKind()).isEqualTo(WebhookEventKind.CLOSED);
        assertThat(event.normativeResolution()).isEqualTo(NormativeResolution.DONE);
        assertThat(event.externalRef()).isEqualTo("PROJ-1234");
        assertThat(event.trackerType()).isEqualTo("jira");
        assertThat(event.actor()).isEqualTo("Alice");
    }

    @Test
    void resolvedWontDo_parsesDecline() throws IOException {
        final WebhookEvent event = parser.parse(Map.of(), fixture("issue-resolved-wont-do.json"));

        assertThat(event.normativeResolution()).isEqualTo(NormativeResolution.DECLINE);
    }

    @Test
    void resolvedCannotReproduce_parsesFailure() throws IOException {
        final WebhookEvent event = parser.parse(Map.of(), fixture("issue-resolved-cannot-reproduce.json"));

        assertThat(event.normativeResolution()).isEqualTo(NormativeResolution.FAILURE);
    }

    @Test
    void assigned_parsesAssigned() throws IOException {
        final WebhookEvent event = parser.parse(Map.of(), fixture("issue-assigned.json"));

        assertThat(event.eventKind()).isEqualTo(WebhookEventKind.ASSIGNED);
        assertThat(event.newAssignee()).isEqualTo("abc123");
        assertThat(event.actor()).isEqualTo("Bob");
    }

    @Test
    void unassigned_parsesUnassigned() throws IOException {
        final WebhookEvent event = parser.parse(Map.of(), fixture("issue-unassigned.json"));

        assertThat(event.eventKind()).isEqualTo(WebhookEventKind.UNASSIGNED);
    }

    @Test
    void descriptionChanged_parsesDescriptionChanged() throws IOException {
        final WebhookEvent event = parser.parse(Map.of(), fixture("issue-updated-description.json"));

        assertThat(event.eventKind()).isEqualTo(WebhookEventKind.DESCRIPTION_CHANGED);
        assertThat(event.newDescription()).isEqualTo("Updated description text.");
    }

    @Test
    void priorityChanged_highest_parsesUrgent() throws IOException {
        final WebhookEvent event = parser.parse(Map.of(), fixture("issue-updated-priority.json"));

        assertThat(event.eventKind()).isEqualTo(WebhookEventKind.PRIORITY_CHANGED);
        assertThat(event.newPriority()).isEqualTo(WorkItemPriority.URGENT);
    }
}
