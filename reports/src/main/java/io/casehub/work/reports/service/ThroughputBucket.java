package io.casehub.work.reports.service;

public record ThroughputBucket(String period, long created, long completed) {
}
