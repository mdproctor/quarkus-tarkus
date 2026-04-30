package io.casehub.work.reports.service;

import java.util.Map;

public record SlaSummary(
        long totalBreached,
        double avgBreachDurationMinutes,
        Map<String, Long> byCategory) {
}
