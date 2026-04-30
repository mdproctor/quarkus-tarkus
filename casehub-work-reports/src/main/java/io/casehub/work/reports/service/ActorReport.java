package io.casehub.work.reports.service;

import java.util.Map;

public record ActorReport(
        String actorId,
        long totalAssigned,
        long totalCompleted,
        long totalRejected,
        Double avgCompletionMinutes,
        Map<String, Long> byCategory) {
}
