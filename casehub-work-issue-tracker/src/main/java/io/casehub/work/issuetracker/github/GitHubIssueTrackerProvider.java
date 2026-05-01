package io.casehub.work.issuetracker.github;

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
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.runtime.model.WorkItemLabel;
import io.casehub.work.runtime.model.WorkItemPriority;
import io.casehub.work.runtime.model.WorkItemStatus;

import io.casehub.work.issuetracker.spi.ExternalIssueRef;
import io.casehub.work.issuetracker.spi.IssueTrackerException;
import io.casehub.work.issuetracker.spi.IssueTrackerProvider;

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
 * Set {@code casehub.work.issue-tracker.github.token} to a PAT with
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
                        "casehub.work.issue-tracker.github.default-repository is required for createIssue"));

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
                            ". Check casehub.work.issue-tracker.github.token");
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
                                "': casehub.work.issue-tracker.github.default-repository is not set"));
        return new ParsedRef(repo, cleaned);
    }

    /**
     * Synchronise WorkItem fields to the GitHub Issue as labels and state.
     *
     * <p>
     * Computes three namespaced label sets from the WorkItem:
     * <ul>
     *   <li>{@code priority:critical|high|normal|low}
     *   <li>{@code category:<value>} — only when category is set
     *   <li>{@code status:pending|assigned|in-progress|delegated|suspended|escalated}
     *       — omitted for terminal statuses (issue is closed instead)
     *   <li>WorkItem label paths (e.g. {@code legal/contracts/nda})
     * </ul>
     * Managed labels ({@code priority:*}, {@code category:*}, {@code status:*}) are
     * replaced on every sync. User-applied labels on the GitHub Issue are preserved.
     * Labels are auto-created on first use per repository.
     *
     * <p>
     * Issue {@code state} is set to {@code closed} for terminal WorkItem statuses;
     * {@code open} for all others.
     */
    @Override
    public void syncToIssue(final String externalRef, final WorkItem workItem) {
        final ParsedRef ref = parse(externalRef);
        final String issueUrl = API_BASE + "/repos/" + ref.repo() + "/issues/" + ref.number();

        try {
            // Compute new managed labels
            final java.util.List<String> managedLabels = buildManagedLabels(workItem);

            // GET current labels; preserve non-managed ones
            final HttpRequest getReq = newRequest(issueUrl).GET().build();
            final HttpResponse<String> getResp = http.send(getReq, HttpResponse.BodyHandlers.ofString());
            if (getResp.statusCode() >= 400) {
                LOG.warnf("Could not fetch issue %s for sync (HTTP %d)", externalRef, getResp.statusCode());
                return;
            }
            final JsonNode issue = MAPPER.readTree(getResp.body());
            final java.util.List<String> existingLabels = new java.util.ArrayList<>();
            issue.path("labels").forEach(l -> {
                final String name = l.path("name").asText("");
                if (!name.startsWith("priority:") && !name.startsWith("category:")
                        && !name.startsWith("status:")) {
                    existingLabels.add(name); // preserve user-applied labels
                }
            });

            // Auto-create any managed labels that don't yet exist in the repo
            for (final String label : managedLabels) {
                ensureLabel(ref.repo(), label);
            }

            // Merge: existing non-managed + new managed + WorkItem labels
            final java.util.Set<String> finalLabels = new java.util.LinkedHashSet<>(existingLabels);
            finalLabels.addAll(managedLabels);

            // PATCH: labels + state
            final boolean terminal = workItem.status != null && workItem.status.isTerminal();
            final ObjectNode patch = MAPPER.createObjectNode();
            patch.put("state", terminal ? "closed" : "open");
            final ArrayNode labelsArray = patch.putArray("labels");
            finalLabels.forEach(labelsArray::add);

            final HttpRequest patchReq = newRequest(issueUrl)
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(patch)))
                    .header("Content-Type", "application/json")
                    .build();
            final HttpResponse<String> patchResp = http.send(patchReq, HttpResponse.BodyHandlers.ofString());
            if (patchResp.statusCode() >= 400) {
                LOG.warnf("syncToIssue PATCH failed for %s (HTTP %d): %s",
                        externalRef, patchResp.statusCode(), patchResp.body());
            }

        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new IssueTrackerException("syncToIssue failed for " + externalRef, e);
        }
    }

    // ── Label helpers ─────────────────────────────────────────────────────────

    java.util.List<String> buildManagedLabels(final WorkItem workItem) {
        final java.util.List<String> labels = new java.util.ArrayList<>();

        // Priority
        labels.add(priorityLabel(workItem.priority));

        // Category
        if (workItem.category != null && !workItem.category.isBlank()) {
            labels.add("category:" + workItem.category.toLowerCase());
        }

        // Status (omitted for terminal — issue state handles that)
        final String statusLabel = statusLabel(workItem.status);
        if (statusLabel != null) {
            labels.add(statusLabel);
        }

        // WorkItem labels (path used as GitHub label name)
        if (workItem.labels != null) {
            for (final WorkItemLabel label : workItem.labels) {
                if (label.path != null && !label.path.isBlank()) {
                    labels.add(label.path);
                }
            }
        }

        return labels;
    }

    private String priorityLabel(final WorkItemPriority priority) {
        if (priority == null) return "priority:normal";
        return switch (priority) {
            case CRITICAL -> "priority:critical";
            case HIGH -> "priority:high";
            case NORMAL -> "priority:normal";
            case LOW -> "priority:low";
        };
    }

    private String statusLabel(final WorkItemStatus status) {
        if (status == null) return null;
        return switch (status) {
            case PENDING -> "status:pending";
            case ASSIGNED -> "status:assigned";
            case IN_PROGRESS -> "status:in-progress";
            case DELEGATED -> "status:delegated";
            case SUSPENDED -> "status:suspended";
            case ESCALATED -> "status:escalated";
            default -> null; // terminal statuses — issue closed, no status label
        };
    }

    /** Create a GitHub label if it does not already exist. Idempotent — 422 is ignored. */
    private void ensureLabel(final String repo, final String name) {
        try {
            final String color = labelColor(name);
            final ObjectNode payload = MAPPER.createObjectNode()
                    .put("name", name)
                    .put("color", color);
            final HttpRequest req = newRequest(API_BASE + "/repos/" + repo + "/labels")
                    .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(payload)))
                    .header("Content-Type", "application/json")
                    .build();
            http.send(req, HttpResponse.BodyHandlers.ofString());
            // 201 = created, 422 = already exists — both acceptable
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            LOG.warnf("ensureLabel failed for '%s' in %s: %s", name, repo, e.getMessage());
        }
    }

    private String labelColor(final String name) {
        if (name.startsWith("priority:critical")) return "E11D48";
        if (name.startsWith("priority:high")) return "F97316";
        if (name.startsWith("priority:normal")) return "3B82F6";
        if (name.startsWith("priority:low")) return "6B7280";
        if (name.startsWith("status:in-progress")) return "34D399";
        if (name.startsWith("status:assigned")) return "60A5FA";
        if (name.startsWith("status:pending")) return "A8A29E";
        if (name.startsWith("status:suspended")) return "FBBF24";
        if (name.startsWith("status:delegated")) return "A78BFA";
        if (name.startsWith("status:escalated")) return "F43F5E";
        if (name.startsWith("category:")) return "D1FAE5";
        return "CCCCCC"; // default for WorkItem label paths
    }

    private record ParsedRef(String repo, String number) {
    }
}
