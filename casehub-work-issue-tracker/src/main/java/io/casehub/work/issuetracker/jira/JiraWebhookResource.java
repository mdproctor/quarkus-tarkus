package io.casehub.work.issuetracker.jira;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.jboss.logging.Logger;

import io.casehub.work.issuetracker.webhook.WebhookEvent;
import io.casehub.work.issuetracker.webhook.WebhookEventHandler;

/**
 * Receives inbound Jira webhook events.
 *
 * <p>Jira Cloud does not sign payloads with HMAC. Verification uses a shared secret
 * passed as a query parameter. Configure your Jira webhook URL as:
 * {@code https://yourapp.example.com/workitems/jira-webhook?secret=your-secret}
 *
 * <p>Returns 200 for all valid requests (including unhandled events) to prevent
 * Jira retry storms. Returns 401 on secret mismatch or missing config.
 *
 * <p>Configure in {@code application.properties}:
 * <pre>
 * casehub.work.issue-tracker.jira.webhook-secret=your-secret
 * </pre>
 */
@Path("/workitems/jira-webhook")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class JiraWebhookResource {

    private static final Logger LOG = Logger.getLogger(JiraWebhookResource.class);

    @Inject
    JiraIssueTrackerConfig config;

    @Inject
    JiraWebhookParser parser;

    @Inject
    WebhookEventHandler handler;

    /**
     * Receive a Jira webhook event.
     *
     * @param secret the {@code secret} query parameter for verification
     * @param body the raw JSON payload
     * @return 200 OK on success or unhandled event, 401 on secret mismatch
     */
    @POST
    public Response receive(
            @QueryParam("secret") final String secret,
            final String body) {

        final String configuredSecret = config.webhookSecret()
                .filter(s -> !s.isBlank())
                .orElse(null);
        if (configuredSecret == null) {
            LOG.warn("Jira webhook received but casehub.work.issue-tracker.jira.webhook-secret is not configured — rejecting");
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        if (!verifySecret(configuredSecret, secret)) {
            LOG.warn("Jira webhook secret mismatch — rejecting");
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        try {
            // Jira does not send event type headers; event kind is derived from the payload.
            final WebhookEvent event = parser.parse(Map.of(), body);
            if (event != null) {
                handler.handle(event);
            }
        } catch (final Exception e) {
            LOG.warnf("Jira webhook processing error (returning 200 to prevent retry): %s", e.getMessage());
        }

        return Response.ok().build();
    }

    private boolean verifySecret(final String expected, final String provided) {
        if (provided == null || provided.isBlank()) return false;
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8));
    }
}
