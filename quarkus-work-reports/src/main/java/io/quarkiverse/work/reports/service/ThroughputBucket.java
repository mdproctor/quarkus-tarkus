package io.quarkiverse.work.reports.service;

public record ThroughputBucket(String period, long created, long completed) {
}
