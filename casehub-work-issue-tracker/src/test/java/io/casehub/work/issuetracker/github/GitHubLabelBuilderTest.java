package io.casehub.work.issuetracker.github;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.runtime.model.WorkItemLabel;
import io.casehub.work.runtime.model.WorkItemPriority;
import io.casehub.work.runtime.model.WorkItemStatus;

/**
 * Pure unit tests for label computation in {@link GitHubIssueTrackerProvider}.
 *
 * <p>
 * Uses a subclass to expose the package-private {@code buildManagedLabels} method,
 * avoiding Quarkus boot and network calls.
 *
 * <h2>Coverage</h2>
 * <ul>
 *   <li>Happy path — all label types produced correctly
 *   <li>Correctness — all priorities, all non-terminal statuses, category, WorkItem labels
 *   <li>Robustness — null priority, null status, null category, no labels
 * </ul>
 */
class GitHubLabelBuilderTest {

    /** Subclass exposes the package-private buildManagedLabels for direct testing. */
    static class TestableProvider extends GitHubIssueTrackerProvider {
        List<String> labels(final WorkItem wi) {
            return buildManagedLabels(wi);
        }
    }

    private TestableProvider provider;

    @BeforeEach
    void setUp() {
        provider = new TestableProvider();
    }

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    void labels_includeAllThreeNamespaces() {
        final WorkItem wi = workItem(WorkItemStatus.IN_PROGRESS, WorkItemPriority.HIGH, "finance");
        final List<String> labels = provider.labels(wi);

        assertThat(labels).contains("priority:high", "category:finance", "status:in-progress");
    }

    // ── Correctness — priority ────────────────────────────────────────────────

    @Test
    void priority_critical() {
        assertThat(provider.labels(workItem(WorkItemStatus.PENDING, WorkItemPriority.CRITICAL, null)))
                .contains("priority:critical");
    }

    @Test
    void priority_high() {
        assertThat(provider.labels(workItem(WorkItemStatus.PENDING, WorkItemPriority.HIGH, null)))
                .contains("priority:high");
    }

    @Test
    void priority_normal() {
        assertThat(provider.labels(workItem(WorkItemStatus.PENDING, WorkItemPriority.NORMAL, null)))
                .contains("priority:normal");
    }

    @Test
    void priority_low() {
        assertThat(provider.labels(workItem(WorkItemStatus.PENDING, WorkItemPriority.LOW, null)))
                .contains("priority:low");
    }

    @Test
    void priority_null_defaultsToNormal() {
        assertThat(provider.labels(workItem(WorkItemStatus.PENDING, null, null)))
                .contains("priority:normal");
    }

    // ── Correctness — status ──────────────────────────────────────────────────

    @Test
    void status_pending() {
        assertThat(provider.labels(workItem(WorkItemStatus.PENDING, WorkItemPriority.NORMAL, null)))
                .contains("status:pending");
    }

    @Test
    void status_assigned() {
        assertThat(provider.labels(workItem(WorkItemStatus.ASSIGNED, WorkItemPriority.NORMAL, null)))
                .contains("status:assigned");
    }

    @Test
    void status_inProgress() {
        assertThat(provider.labels(workItem(WorkItemStatus.IN_PROGRESS, WorkItemPriority.NORMAL, null)))
                .contains("status:in-progress");
    }

    @Test
    void status_delegated() {
        assertThat(provider.labels(workItem(WorkItemStatus.DELEGATED, WorkItemPriority.NORMAL, null)))
                .contains("status:delegated");
    }

    @Test
    void status_suspended() {
        assertThat(provider.labels(workItem(WorkItemStatus.SUSPENDED, WorkItemPriority.NORMAL, null)))
                .contains("status:suspended");
    }

    @Test
    void status_escalated() {
        assertThat(provider.labels(workItem(WorkItemStatus.ESCALATED, WorkItemPriority.NORMAL, null)))
                .contains("status:escalated");
    }

    @Test
    void status_terminal_noStatusLabel() {
        for (final WorkItemStatus terminal : List.of(
                WorkItemStatus.COMPLETED, WorkItemStatus.REJECTED,
                WorkItemStatus.CANCELLED, WorkItemStatus.EXPIRED)) {
            final List<String> labels = provider.labels(workItem(terminal, WorkItemPriority.NORMAL, null));
            assertThat(labels).noneMatch(l -> l.startsWith("status:"))
                    .as("terminal status %s should produce no status label", terminal);
        }
    }

    // ── Correctness — category ────────────────────────────────────────────────

    @Test
    void category_producesLabel() {
        assertThat(provider.labels(workItem(WorkItemStatus.PENDING, WorkItemPriority.NORMAL, "legal")))
                .contains("category:legal");
    }

    @Test
    void category_isLowercased() {
        assertThat(provider.labels(workItem(WorkItemStatus.PENDING, WorkItemPriority.NORMAL, "Finance")))
                .contains("category:finance");
    }

    @Test
    void category_null_noLabel() {
        assertThat(provider.labels(workItem(WorkItemStatus.PENDING, WorkItemPriority.NORMAL, null)))
                .noneMatch(l -> l.startsWith("category:"));
    }

    @Test
    void category_blank_noLabel() {
        assertThat(provider.labels(workItem(WorkItemStatus.PENDING, WorkItemPriority.NORMAL, "  ")))
                .noneMatch(l -> l.startsWith("category:"));
    }

    // ── Correctness — WorkItem labels ─────────────────────────────────────────

    @Test
    void workItemLabels_addedToManagedLabels() {
        final WorkItem wi = workItem(WorkItemStatus.PENDING, WorkItemPriority.NORMAL, null);
        wi.labels.add(new WorkItemLabel("legal/contracts/nda",
                io.casehub.work.runtime.model.LabelPersistence.MANUAL, "alice"));
        wi.labels.add(new WorkItemLabel("finance/approval",
                io.casehub.work.runtime.model.LabelPersistence.INFERRED, "filter-1"));

        final List<String> labels = provider.labels(wi);

        assertThat(labels).contains("legal/contracts/nda", "finance/approval");
    }

    @Test
    void workItemLabels_null_noError() {
        final WorkItem wi = workItem(WorkItemStatus.PENDING, WorkItemPriority.NORMAL, null);
        wi.labels = null;

        assertThat(provider.labels(wi)).isNotEmpty(); // priority label at minimum
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private WorkItem workItem(final WorkItemStatus status, final WorkItemPriority priority,
            final String category) {
        final WorkItem wi = new WorkItem();
        wi.status = status;
        wi.priority = priority;
        wi.category = category;
        return wi;
    }
}
