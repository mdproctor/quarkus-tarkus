package io.casehub.work.reports.service;

import java.util.List;

public record SlaBreachReport(List<SlaBreachItem> items, SlaSummary summary) {
}
