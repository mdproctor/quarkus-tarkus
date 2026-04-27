package io.quarkiverse.work.reports.service;

import java.time.Instant;

public record QueueHealthReport(
        Instant timestamp,
        long overdueCount,
        long pendingCount,
        long avgPendingAgeSeconds,
        Instant oldestUnclaimedCreatedAt,
        long criticalOverdueCount) {
}
