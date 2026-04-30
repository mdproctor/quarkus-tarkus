package io.casehub.work.notifications.channel;

import java.util.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.casehub.connectors.ConnectorMessage;
import io.casehub.connectors.slack.SlackConnector;
import io.casehub.work.api.NotificationChannel;
import io.casehub.work.api.NotificationPayload;
import io.casehub.work.runtime.model.WorkItem;

/**
 * Slack {@link NotificationChannel} — delegates to {@link SlackConnector}
 * from {@code casehub-connectors}.
 *
 * <p>
 * Message format: {@code "[{eventType}] {title} ({category}) — {status}"}.
 * No Slack SDK required; no credentials needed beyond the webhook URL in the rule.
 */
@ApplicationScoped
public class SlackNotificationChannel implements NotificationChannel {

    private static final Logger LOG = Logger.getLogger(SlackNotificationChannel.class.getName());

    @Inject
    SlackConnector slackConnector;

    @Override
    public String channelType() {
        return SlackConnector.ID;
    }

    @Override
    public void send(final NotificationPayload payload) {
        try {
            final WorkItem wi = (WorkItem) payload.event().source();
            final String eventType = payload.event().eventType().name();
            final String title = "[" + eventType + "] " + wi.title;
            final String body = "Category: " + wi.category
                    + " | Status: " + (wi.status != null ? wi.status.name() : "—")
                    + (wi.assigneeId != null ? " | Assignee: " + wi.assigneeId : "");

            slackConnector.send(new ConnectorMessage(payload.targetUrl(), title, body));
        } catch (final Exception e) {
            LOG.warning("SlackNotificationChannel error for rule " + payload.ruleId()
                    + ": " + e.getMessage());
        }
    }
}
