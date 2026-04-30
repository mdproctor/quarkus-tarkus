package io.casehub.work.reports.service;

import java.time.Instant;

public record SlaBreachItem(
        String workItemId,
        String category,
        String priority,
        Instant expiresAt,
        Instant completedAt,
        String status,
        long breachDurationMinutes) {
}
