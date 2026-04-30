package io.casehub.work.notifications.channel;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.casehubio.connectors.slack.SlackConnector;

/**
 * Unit tests for Slack payload building — delegates to casehub-connectors SlackConnector.
 */
class SlackChannelTest {

    @Test
    void buildSlackPayload_containsEventAndTitle() {
        final String json = SlackConnector.buildPayload(
                "[ASSIGNED] Loan review", "loan-application | Status: ASSIGNED | Assignee: alice");
        assertThat(json).contains("ASSIGNED");
        assertThat(json).contains("Loan review");
        assertThat(json).contains("loan-application");
    }

    @Test
    void buildSlackPayload_validJson() {
        final String json = SlackConnector.buildPayload("Title", "Body text");
        assertThat(json.trim()).startsWith("{").endsWith("}");
        assertThat(json).contains("\"text\"");
    }

    @Test
    void buildSlackPayload_escapesSpecialChars() {
        final String json = SlackConnector.buildPayload("Review \"special\" item", "body");
        assertThat(json).doesNotContain("\"Review \"special\"");
    }

    @Test
    void channelType_isSlack() {
        assertThat(SlackConnector.ID).isEqualTo("slack");
    }
}
