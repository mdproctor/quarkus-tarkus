package io.casehub.work.notifications.channel;

import java.util.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.casehubio.connectors.ConnectorMessage;
import io.casehubio.connectors.teams.TeamsConnector;
import io.casehub.work.api.NotificationChannel;
import io.casehub.work.api.NotificationPayload;
import io.casehub.work.runtime.model.WorkItem;

/**
 * Microsoft Teams {@link NotificationChannel} — delegates to {@link TeamsConnector}
 * from {@code casehub-connectors}.
 *
 * <p>
 * Sends an Adaptive Card to the configured Teams Incoming Webhook URL.
 * No credentials needed beyond the webhook URL in the rule.
 */
@ApplicationScoped
public class TeamsNotificationChannel implements NotificationChannel {

    private static final Logger LOG = Logger.getLogger(TeamsNotificationChannel.class.getName());

    @Inject
    TeamsConnector teamsConnector;

    @Override
    public String channelType() {
        return TeamsConnector.ID;
    }

    @Override
    public void send(final NotificationPayload payload) {
        try {
            final WorkItem wi = (WorkItem) payload.event().source();
            final String eventType = payload.event().eventType().name();
            final String title = "[" + eventType + "] " + wi.title;
            final String body = "Category: " + wi.category
                    + " | Status: " + (wi.status != null ? wi.status.name() : "—")
                    + (wi.assigneeId != null ? " | Assignee: " + wi.assigneeId : "")
                    + (wi.priority != null ? " | Priority: " + wi.priority.name() : "");

            teamsConnector.send(new ConnectorMessage(payload.targetUrl(), title, body));
        } catch (final Exception e) {
            LOG.warning("TeamsNotificationChannel error for rule " + payload.ruleId()
                    + ": " + e.getMessage());
        }
    }
}
