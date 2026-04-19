package io.quarkiverse.workitems.issuetracker.github;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

/**
 * Configuration for the GitHub issue tracker integration.
 *
 * <pre>{@code
 * # Personal access token with repo scope (or fine-grained: issues:write)
 * quarkus.workitems.issue-tracker.github.token=ghp_...
 *
 * # Default repository when externalRef does not include owner/repo
 * quarkus.workitems.issue-tracker.github.default-repository=myorg/myapp
 *
 * # When true, linked GitHub issues are closed when the WorkItem is COMPLETED
 * quarkus.workitems.issue-tracker.github.auto-close-on-complete=false
 * }</pre>
 */
@ConfigMapping(prefix = "quarkus.workitems.issue-tracker.github")
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
public interface GitHubIssueTrackerConfig {

    /**
     * GitHub personal access token (classic or fine-grained).
     * Required scopes: {@code repo} (classic) or {@code issues: write} (fine-grained).
     * If absent, the provider is enabled but all remote calls will fail with an auth error.
     *
     * @return the token, or empty if not configured
     */
    Optional<String> token();

    /**
     * Default repository in {@code owner/repo} format.
     * Used when creating issues and when an {@code externalRef} does not include the repo
     * component (i.e. it is a bare issue number like {@code "42"} rather than
     * {@code "myorg/myapp#42"}).
     *
     * @return the default repository, or empty if not configured
     */
    @WithName("default-repository")
    Optional<String> defaultRepository();

    /**
     * When {@code true}, the linked GitHub issue is automatically closed (with a comment)
     * when the WorkItem transitions to {@link io.quarkiverse.workitems.runtime.model.WorkItemStatus#COMPLETED}.
     * Defaults to {@code false} — explicit close via the REST API or via
     * {@link GitHubIssueTrackerProvider#closeIssue} only.
     *
     * @return whether to auto-close issues on WorkItem completion
     */
    @WithName("auto-close-on-complete")
    @WithDefault("false")
    boolean autoCloseOnComplete();
}
