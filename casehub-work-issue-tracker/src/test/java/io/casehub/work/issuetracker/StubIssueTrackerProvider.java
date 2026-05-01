package io.casehub.work.issuetracker;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import io.casehub.work.issuetracker.spi.ExternalIssueRef;
import io.casehub.work.issuetracker.spi.IssueTrackerException;
import io.casehub.work.issuetracker.spi.IssueTrackerProvider;
import io.casehub.work.runtime.model.WorkItem;

/**
 * Test-only stub that replaces the real GitHub provider with an in-memory implementation.
 * No network calls; no credentials required.
 *
 * <p>
 * Issues can be seeded via {@link #seed} and inspected via {@link #closed} and
 * {@link #created}. Call {@link #reset()} in {@code @BeforeEach}.
 */
@ApplicationScoped
@Alternative
@Priority(1)
public class StubIssueTrackerProvider implements IssueTrackerProvider {

    private final Map<String, StubIssue> issues = new ConcurrentHashMap<>();
    private final List<String> created = new ArrayList<>();
    private final List<String> closed = new ArrayList<>();
    private final List<SyncCall> synced = new ArrayList<>();
    private final AtomicInteger nextNumber = new AtomicInteger(100);

    public record StubIssue(String ref, String title, String url, String status) {}

    public record SyncCall(String externalRef, WorkItem workItem) {}

    /** Pre-populate an issue that fetchIssue() will return. */
    public void seed(final String externalRef, final String title, final String status) {
        issues.put(externalRef, new StubIssue(
                externalRef, title, "https://stub/" + externalRef, status));
    }

    /** Clear all state. Call in @BeforeEach. */
    public void reset() {
        issues.clear();
        created.clear();
        closed.clear();
        synced.clear();
    }

    /** Returns refs of all issues created via createIssue(). */
    public List<String> created() {
        return List.copyOf(created);
    }

    /** Returns refs of all issues closed via closeIssue(). */
    public List<String> closed() {
        return List.copyOf(closed);
    }

    /** Returns all syncToIssue calls in order. */
    public List<SyncCall> synced() {
        return List.copyOf(synced);
    }

    @Override
    public String trackerType() {
        return "github";
    }

    @Override
    public ExternalIssueRef fetchIssue(final String externalRef) {
        final StubIssue issue = issues.get(externalRef);
        if (issue == null) {
            throw IssueTrackerException.notFound(externalRef);
        }
        return new ExternalIssueRef(trackerType(), externalRef, issue.title(), issue.url(), issue.status());
    }

    @Override
    public Optional<String> createIssue(final UUID workItemId, final String title, final String body) {
        final String ref = "stub/repo#" + nextNumber.getAndIncrement();
        issues.put(ref, new StubIssue(ref, title, "https://stub/" + ref, "open"));
        created.add(ref);
        return Optional.of(ref);
    }

    @Override
    public void syncToIssue(final String externalRef, final WorkItem workItem) {
        synced.add(new SyncCall(externalRef, workItem));
    }

    @Override
    public void closeIssue(final String externalRef, final String resolution) {
        final StubIssue issue = issues.get(externalRef);
        if (issue == null) {
            throw IssueTrackerException.notFound(externalRef);
        }
        issues.put(externalRef, new StubIssue(
                externalRef, issue.title(), issue.url(), "closed"));
        closed.add(externalRef);
    }
}
