package io.casehub.work.issuetracker.github;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.jboss.logging.Logger;

import io.casehub.work.issuetracker.webhook.WebhookEvent;
import io.casehub.work.issuetracker.webhook.WebhookEventHandler;

/**
 * Receives inbound GitHub Issues webhook events.
 *
 * <p>Verifies the {@code X-Hub-Signature-256} HMAC before processing.
 * Returns 200 for all valid requests (including unhandled event types) to
 * prevent GitHub retry storms. Returns 401 on signature failure or missing secret config.
 *
 * <p>Configure in {@code application.properties}:
 * <pre>
 * casehub.work.issue-tracker.github.webhook-secret=your-secret
 * </pre>
 */
@Path("/workitems/github-webhook")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class GitHubWebhookResource {

    private static final Logger LOG = Logger.getLogger(GitHubWebhookResource.class);
    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final HexFormat HEX = HexFormat.of();

    @Inject
    GitHubIssueTrackerConfig config;

    @Inject
    GitHubWebhookParser parser;

    @Inject
    WebhookEventHandler handler;

    /**
     * Receive a GitHub Issues webhook event.
     *
     * @param signature the {@code X-Hub-Signature-256} header value
     * @param body the raw JSON payload
     * @return 200 OK on success or unhandled event, 401 on signature failure
     */
    @POST
    public Response receive(
            @HeaderParam("X-Hub-Signature-256") final String signature,
            final String body) {

        final String secret = config.webhookSecret().filter(s -> !s.isBlank()).orElse(null);
        if (secret == null) {
            LOG.warn("GitHub webhook received but casehub.work.issue-tracker.github.webhook-secret is not configured — rejecting");
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        if (!verifySignature(secret, body, signature)) {
            LOG.warn("GitHub webhook HMAC verification failed — rejecting");
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        try {
            // GitHub event type is read from the action field in the JSON body;
            // X-GitHub-Event header is not currently consumed by the parser.
            final WebhookEvent event = parser.parse(Map.of(), body);
            if (event != null) {
                handler.handle(event);
            }
        } catch (final Exception e) {
            LOG.warnf("GitHub webhook processing error (returning 200 to prevent retry): %s", e.getMessage());
        }

        return Response.ok().build();
    }

    private boolean verifySignature(final String secret, final String body, final String signature) {
        if (signature == null || signature.isBlank()) return false;
        try {
            final Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            final String expected = "sha256=" +
                    HEX.formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8),
                    signature.getBytes(StandardCharsets.UTF_8));
        } catch (final Exception e) {
            LOG.warnf("HMAC computation failed: %s", e.getMessage());
            return false;
        }
    }
}
