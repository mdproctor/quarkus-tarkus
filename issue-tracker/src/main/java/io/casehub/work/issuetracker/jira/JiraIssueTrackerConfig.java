package io.casehub.work.issuetracker.jira;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;

/**
 * Configuration for the Jira issue tracker integration.
 *
 * <pre>{@code
 * # Shared secret verified against the ?secret= query parameter
 * casehub.work.issue-tracker.jira.webhook-secret=mysecret
 *
 * # Jira base URL (required for outbound calls — not needed for inbound webhooks only)
 * casehub.work.issue-tracker.jira.base-url=https://myorg.atlassian.net
 *
 * # Jira API token (email:token for Cloud, PAT for Server)
 * casehub.work.issue-tracker.jira.token=user@example.com:mytoken
 * }</pre>
 *
 * <h2>Webhook verification</h2>
 * <p>
 * Jira Cloud does not sign webhook payloads with HMAC. Instead, configure
 * the webhook URL with a secret query parameter:
 * {@code https://yourapp.example.com/workitems/jira-webhook?secret=mysecret}
 * and set {@code casehub.work.issue-tracker.jira.webhook-secret} to the same value.
 * If not configured, inbound webhooks are rejected (fail-closed).
 */
@ConfigMapping(prefix = "casehub.work.issue-tracker.jira")
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
public interface JiraIssueTrackerConfig {

    /**
     * Shared secret verified against the {@code secret} query parameter.
     * Fail-closed: if not configured or blank, all inbound webhooks are rejected.
     *
     * @return the webhook secret, or empty if not configured
     */
    @WithName("webhook-secret")
    Optional<String> webhookSecret();

    /**
     * Jira base URL, e.g. {@code https://myorg.atlassian.net}.
     *
     * @return the base URL, or empty if not configured
     */
    @WithName("base-url")
    Optional<String> baseUrl();

    /**
     * Jira API token. For Cloud: {@code email:api-token}. For Server: PAT.
     *
     * @return the token, or empty if not configured
     */
    Optional<String> token();
}
