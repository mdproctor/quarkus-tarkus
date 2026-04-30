package io.casehub.work.reports.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.IsoFields;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ThroughputBucketAggregator {

    private ThroughputBucketAggregator() {
    }

    /**
     * Merge day-granularity DB result rows into period buckets.
     *
     * @param dayCreated rows from HQL: [LocalDate, Long created count]
     * @param dayCompleted rows from HQL: [LocalDate, Long completed count]
     * @param groupBy "day", "week", or "month"
     */
    public static List<ThroughputBucket> aggregate(
            List<Object[]> dayCreated,
            List<Object[]> dayCompleted,
            String groupBy) {

        final Map<String, long[]> buckets = new LinkedHashMap<>();

        for (final Object[] row : dayCreated) {
            final String period = toPeriodLabel((LocalDate) row[0], groupBy);
            buckets.computeIfAbsent(period, k -> new long[2])[0] += (Long) row[1];
        }
        for (final Object[] row : dayCompleted) {
            final String period = toPeriodLabel((LocalDate) row[0], groupBy);
            buckets.computeIfAbsent(period, k -> new long[2])[1] += (Long) row[1];
        }

        return buckets.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> new ThroughputBucket(e.getKey(), e.getValue()[0], e.getValue()[1]))
                .toList();
    }

    private static String toPeriodLabel(final LocalDate date, final String groupBy) {
        return switch (groupBy) {
            case "week" -> date.getYear() + "-W"
                    + String.format("%02d", date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
            case "month" -> DateTimeFormatter.ofPattern("yyyy-MM").format(date);
            default -> date.toString();
        };
    }
}
