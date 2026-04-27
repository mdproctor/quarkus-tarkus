package io.quarkiverse.work.reports.service;

import java.time.Instant;
import java.util.List;

public record ThroughputReport(Instant from, Instant to, String groupBy, List<ThroughputBucket> buckets) {
}
