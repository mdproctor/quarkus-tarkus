package io.quarkiverse.work.runtime.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.quarkiverse.work.runtime.model.WorkItem;
import io.quarkiverse.work.runtime.model.WorkItemStatus;

/**
 * Pure unit tests for WorkItemMetrics gauge functions — no Quarkus, no DB.
 */
class WorkItemMetricsTest {

    // ── countNonTerminal ──────────────────────────────────────────────────────

    @Test
    void countNonTerminal_excludesTerminalStatuses() {
        final List<WorkItem> items = List.of(
                wi(WorkItemStatus.PENDING),
                wi(WorkItemStatus.ASSIGNED),
                wi(WorkItemStatus.IN_PROGRESS),
                wi(WorkItemStatus.COMPLETED), // terminal
                wi(WorkItemStatus.REJECTED), // terminal
                wi(WorkItemStatus.CANCELLED)); // terminal

        assertThat(WorkItemMetrics.countNonTerminal(items)).isEqualTo(3);
    }

    @Test
    void countNonTerminal_emptyList_returnsZero() {
        assertThat(WorkItemMetrics.countNonTerminal(List.of())).isEqualTo(0);
    }

    // ── countByStatus ─────────────────────────────────────────────────────────

    @Test
    void countByStatus_countsCorrectly() {
        final List<WorkItem> items = List.of(
                wi(WorkItemStatus.PENDING),
                wi(WorkItemStatus.PENDING),
                wi(WorkItemStatus.ASSIGNED),
                wi(WorkItemStatus.IN_PROGRESS));

        assertThat(WorkItemMetrics.countByStatus(items, WorkItemStatus.PENDING)).isEqualTo(2);
        assertThat(WorkItemMetrics.countByStatus(items, WorkItemStatus.ASSIGNED)).isEqualTo(1);
        assertThat(WorkItemMetrics.countByStatus(items, WorkItemStatus.IN_PROGRESS)).isEqualTo(1);
        assertThat(WorkItemMetrics.countByStatus(items, WorkItemStatus.COMPLETED)).isEqualTo(0);
    }

    // ── countOverdue ──────────────────────────────────────────────────────────

    @Test
    void countOverdue_countsNonTerminalPastExpiresAt() {
        final Instant now = Instant.now();
        final List<WorkItem> items = List.of(
                wiWithDeadlines(WorkItemStatus.PENDING, now.minusSeconds(10), null), // overdue
                wiWithDeadlines(WorkItemStatus.IN_PROGRESS, now.minusSeconds(1), null), // overdue
                wiWithDeadlines(WorkItemStatus.PENDING, now.plusSeconds(3600), null), // not overdue
                wiWithDeadlines(WorkItemStatus.PENDING, null, null), // no deadline
                wiWithDeadlines(WorkItemStatus.COMPLETED, now.minusSeconds(10), null)); // terminal — excluded

        assertThat(WorkItemMetrics.countOverdue(items, now)).isEqualTo(2);
    }

    // ── countClaimDeadlineBreached ────────────────────────────────────────────

    @Test
    void countClaimDeadlineBreached_countsPendingPastClaimDeadline() {
        final Instant now = Instant.now();
        final List<WorkItem> items = List.of(
                wiWithDeadlines(WorkItemStatus.PENDING, null, now.minusSeconds(60)), // breached
                wiWithDeadlines(WorkItemStatus.PENDING, null, now.plusSeconds(3600)), // not breached
                wiWithDeadlines(WorkItemStatus.ASSIGNED, null, now.minusSeconds(60)), // ASSIGNED — excluded
                wiWithDeadlines(WorkItemStatus.PENDING, null, null)); // no deadline

        assertThat(WorkItemMetrics.countClaimDeadlineBreached(items, now)).isEqualTo(1);
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private WorkItem wi(final WorkItemStatus status) {
        final WorkItem wi = new WorkItem();
        wi.id = UUID.randomUUID();
        wi.status = status;
        return wi;
    }

    private WorkItem wiWithDeadlines(final WorkItemStatus status,
            final Instant expiresAt, final Instant claimDeadline) {
        final WorkItem wi = wi(status);
        wi.expiresAt = expiresAt;
        wi.claimDeadline = claimDeadline;
        return wi;
    }
}
