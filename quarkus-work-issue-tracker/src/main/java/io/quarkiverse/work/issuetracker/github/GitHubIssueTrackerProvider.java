package io.quarkiverse.work.issuetracker.github;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.quarkiverse.work.issuetracker.spi.ExternalIssueRef;
import io.quarkiverse.work.issuetracker.spi.IssueTrackerException;
import io.quarkiverse.work.issuetracker.spi.IssueTrackerProvider;

/**
 * Default {@link IssueTrackerProvider} for GitHub Issues.
 *
 * <h2>externalRef format</h2>
 * <p>
 * {@code "owner/repo#42"} — self-contained so no per-link config is needed.
 * The {@code owner/repo} part defaults to
 * {@link GitHubIssueTrackerConfig#defaultRepository()} when the ref is a bare
 * number or {@code "#42"} shorthand.
 *
 * <h2>Authentication</h2>
 * <p>
 * Set {@code quarkus.work.issue-tracker.github.token} to a PAT with
 * {@code repo} scope (classic) or {@code issues: write} (fine-grained).
 * Unauthenticated requests hit GitHub's 60 req/hour rate limit.
 *
 * <h2>Replacing this implementation</h2>
 * <p>
 * To use a different "github" implementation (e.g. GitHub Enterprise, a test double):
 *
 * <pre>
 * {
 *     &#64;code
 *     &#64;ApplicationScoped
 *     &#64;Alternative
 *     &#64;Priority(1)
 *     public class MyGitHubProvider implements IssueTrackerProvider {
 *         @Override
 *         public String trackerType() {
 *             return "github";
 *         }
 *         // ...
 *     }
 * }
 * </pre>
 */
@ApplicationScoped
public class GitHubIssueTrackerProvider implements IssueTrackerProvider {

    private static final Logger LOG = Logger.getLogger(GitHubIssueTrackerProvider.class);
    private static final String API_BASE = "https://api.github.com";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Inject
    GitHubIssueTrackerConfig config;

    private final HttpClient http = HttpClient.newHttpClient();

    @Override
    public String trackerType() {
        return "github";
    }

    /**
     * Fetch the current state of a GitHub issue.
     *
     * @param externalRef {@code "owner/repo#42"} format
     * @return snapshot of the issue's current state
     * @throws IssueTrackerException on auth failure, not-found, or network error
     */
    @Override
    public ExternalIssueRef fetchIssue(final String externalRef) {
        final ParsedRef ref = parse(externalRef);
        final String url = API_BASE + "/repos/" + ref.repo() + "/issues/" + ref.number();

        try {
            final HttpRequest request = newRequest(url).GET().build();
            final HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 404) {
                throw IssueTrackerException.notFound(externalRef);
            }
            requireSuccess(response, externalRef);

            final JsonNode body = MAPPER.readTree(response.body());
            return new ExternalIssueRef(
                    trackerType(),
                    externalRef,
                    body.path("title").asText(""),
                    body.path("html_url").asText(""),
                    mapState(body.path("state").asText("unknown")));

        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IssueTrackerException("GitHub API call failed for " + externalRef, e);
        }
    }

    /**
     * Create a new GitHub issue and return its {@code externalRef}.
     *
     * <p>
     * The WorkItem UUID is appended to the body as a back-reference so the GitHub
     * issue links back to the operational task:
     *
     * <pre>
     * ...body text...
     *
     * ---
     * *Linked WorkItem: `{workItemId}`*
     * </pre>
     *
     * @param workItemId the UUID of the WorkItem to back-reference in the issue body
     * @param title the issue title
     * @param body the issue body (GitHub Markdown)
     * @return the {@code externalRef} of the new issue ({@code "owner/repo#N"})
     * @throws IssueTrackerException if the API call fails or no token/repo is configured
     */
    @Override
    public Optional<String> createIssue(final UUID workItemId, final String title, final String body) {
        final String repo = config.defaultRepository()
                .orElseThrow(() -> new IssueTrackerException(
                        "quarkus.work.issue-tracker.github.default-repository is required for createIssue"));

        final String fullBody = body + "\n\n---\n*Linked WorkItem: `" + workItemId + "`*";
        final String url = API_BASE + "/repos/" + repo + "/issues";

        try {
            final ObjectNode payload = MAPPER.createObjectNode()
                    .put("title", title)
                    .put("body", fullBody);

            final HttpRequest request = newRequest(url)
                    .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(payload)))
                    .header("Content-Type", "application/json")
                    .build();

            final HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            requireSuccess(response, "create issue in " + repo);

            final JsonNode created = MAPPER.readTree(response.body());
            final int number = created.path("number").asInt();
            return Optional.of(repo + "#" + number);

        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IssueTrackerException("Failed to create GitHub issue in " + repo, e);
        }
    }

    /**
     * Close a GitHub issue, optionally posting a resolution comment first.
     *
     * @param externalRef {@code "owner/repo#42"} format
     * @param resolution if non-null, posted as a comment before closing
     * @throws IssueTrackerException if the API calls fail
     */
    @Override
    public void closeIssue(final String externalRef, final String resolution) {
        final ParsedRef ref = parse(externalRef);
        final String issueUrl = API_BASE + "/repos/" + ref.repo() + "/issues/" + ref.number();

        try {
            // Post resolution comment if provided
            if (resolution != null && !resolution.isBlank()) {
                final ObjectNode comment = MAPPER.createObjectNode().put("body", resolution);
                final HttpRequest commentReq = newRequest(issueUrl + "/comments")
                        .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(comment)))
                        .header("Content-Type", "application/json")
                        .build();
                final HttpResponse<String> commentResp = http.send(commentReq, HttpResponse.BodyHandlers.ofString());
                if (commentResp.statusCode() >= 400) {
                    LOG.warnf("Failed to post resolution comment on %s: HTTP %d",
                            externalRef, commentResp.statusCode());
                }
            }

            // Close the issue
            final ObjectNode close = MAPPER.createObjectNode().put("state", "closed");
            final HttpRequest closeReq = newRequest(issueUrl)
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(close)))
                    .header("Content-Type", "application/json")
                    .build();
            final HttpResponse<String> closeResp = http.send(closeReq, HttpResponse.BodyHandlers.ofString());

            if (closeResp.statusCode() == 404) {
                throw IssueTrackerException.notFound(externalRef);
            }
            requireSuccess(closeResp, externalRef);

        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IssueTrackerException("Failed to close GitHub issue " + externalRef, e);
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private HttpRequest.Builder newRequest(final String url) {
        final HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28");

        config.token().ifPresent(token -> builder.header("Authorization", "Bearer " + token));

        return builder;
    }

    private void requireSuccess(final HttpResponse<String> response, final String context) {
        if (response.statusCode() == 401 || response.statusCode() == 403) {
            throw new IssueTrackerException(
                    "GitHub auth failure (" + response.statusCode() + ") for " + context +
                            ". Check quarkus.work.issue-tracker.github.token");
        }
        if (response.statusCode() >= 400) {
            throw new IssueTrackerException(
                    "GitHub API error " + response.statusCode() + " for " + context +
                            ": " + response.body());
        }
    }

    private String mapState(final String githubState) {
        return switch (githubState) {
            case "open" -> "open";
            case "closed" -> "closed";
            default -> "unknown";
        };
    }

    /**
     * Parse an externalRef into repo + issue number.
     * Supports:
     * <ul>
     * <li>{@code "owner/repo#42"} — explicit repo and number</li>
     * <li>{@code "#42"} or {@code "42"} — bare number; uses defaultRepository config</li>
     * </ul>
     */
    private ParsedRef parse(final String externalRef) {
        final String cleaned = externalRef.startsWith("#") ? externalRef.substring(1) : externalRef;
        final int hashIdx = cleaned.indexOf('#');

        if (hashIdx > 0) {
            // "owner/repo#42"
            final String repo = cleaned.substring(0, hashIdx);
            final String number = cleaned.substring(hashIdx + 1);
            return new ParsedRef(repo, number);
        }

        // bare number — use default repo
        final String repo = config.defaultRepository()
                .orElseThrow(() -> new IssueTrackerException(
                        "Cannot resolve bare issue ref '" + externalRef +
                                "': quarkus.work.issue-tracker.github.default-repository is not set"));
        return new ParsedRef(repo, cleaned);
    }

    private record ParsedRef(String repo, String number) {
    }
}
