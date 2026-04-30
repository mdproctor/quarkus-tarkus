package io.casehub.work.notifications.channel;

import java.util.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;

import io.casehubio.connectors.http.HttpHelper;
import io.casehub.work.api.NotificationChannel;
import io.casehub.work.api.NotificationPayload;
import io.casehub.work.runtime.model.WorkItem;

/**
 * Generic HTTP webhook {@link NotificationChannel}.
 *
 * <p>
 * POSTs a JSON payload to the configured {@code targetUrl}. When a {@code secret}
 * is set on the rule, adds an {@code X-Signature-256: sha256=<hex>} HMAC header
 * using {@link HttpHelper#hmacSha256Hex}.
 *
 * <p>
 * Uses {@link HttpHelper} from {@code casehub-connectors} — no additional
 * HTTP client dependency.
 */
@ApplicationScoped
public class HttpWebhookChannel implements NotificationChannel {

    public static final String CHANNEL_TYPE = "http-webhook";

    private static final Logger LOG = Logger.getLogger(HttpWebhookChannel.class.getName());

    @Override
    public String channelType() {
        return CHANNEL_TYPE;
    }

    @Override
    public void send(final NotificationPayload payload) {
        try {
            final WorkItem wi = (WorkItem) payload.event().source();
            final String eventType = payload.event().eventType().name();
            final String json = buildPayloadJson(eventType, wi.title, wi.category,
                    wi.status != null ? wi.status.name() : null,
                    wi.assigneeId,
                    wi.priority != null ? wi.priority.name() : null,
                    wi.callerRef);

            final String[] headers = payload.secret() != null && !payload.secret().isBlank()
                    ? new String[] { "X-WorkItem-Event", eventType,
                            "X-Signature-256", HttpHelper.hmacSha256Hex(json, payload.secret()) }
                    : new String[] { "X-WorkItem-Event", eventType };

            final boolean ok = HttpHelper.postJson(payload.targetUrl(), json, headers);
            if (!ok) {
                LOG.warning("HttpWebhookChannel failed for rule " + payload.ruleId());
            }
        } catch (final Exception e) {
            LOG.warning("HttpWebhookChannel error for rule " + payload.ruleId()
                    + ": " + e.getMessage());
        }
    }

    // ── package-private statics for unit testing ──────────────────────────────

    static String buildPayloadJson(final String eventType, final String title,
            final String category, final String status, final String assigneeId,
            final String priority, final String callerRef) {
        return "{"
                + "\"eventType\":" + HttpHelper.jsonQuote(eventType) + ","
                + "\"title\":" + HttpHelper.jsonQuote(title) + ","
                + "\"category\":" + HttpHelper.jsonQuote(category) + ","
                + "\"status\":" + HttpHelper.jsonQuote(status) + ","
                + "\"assigneeId\":" + HttpHelper.jsonQuote(assigneeId) + ","
                + "\"priority\":" + HttpHelper.jsonQuote(priority) + ","
                + "\"callerRef\":" + HttpHelper.jsonQuote(callerRef)
                + "}";
    }

    static String hmacSha256Hex(final String payload, final String secret) {
        return HttpHelper.hmacSha256Hex(payload, secret);
    }
}
