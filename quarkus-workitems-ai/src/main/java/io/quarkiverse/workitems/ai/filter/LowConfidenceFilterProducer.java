package io.quarkiverse.workitems.ai.filter;

import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import io.quarkiverse.work.core.filter.ActionDescriptor;
import io.quarkiverse.work.core.filter.FilterDefinition;
import io.quarkiverse.workitems.ai.config.WorkItemsAiConfig;

/**
 * Produces the permanent {@code ai/low-confidence} filter definition.
 *
 * <p>
 * When {@code quarkus.workitems.ai.low-confidence-filter.enabled=true} (default),
 * WorkItems created with {@code confidenceScore} strictly below
 * {@code quarkus.workitems.ai.confidence-threshold} (default 0.7) automatically
 * receive the {@code ai/low-confidence} label (INFERRED persistence).
 *
 * <p>
 * Toggle at runtime without restart:
 * {@code PUT /filter-rules/permanent/enabled?name=ai/low-confidence}
 * with body {@code {"enabled": false}}.
 *
 * @see <a href="https://github.com/mdproctor/quarkus-workitems/issues/114">Issue #114</a>
 */
@ApplicationScoped
public class LowConfidenceFilterProducer {

    @Inject
    WorkItemsAiConfig config;

    /**
     * Produces the low-confidence routing filter as a permanent CDI-managed definition.
     *
     * @return the filter definition for the low-confidence routing rule
     */
    @Produces
    public FilterDefinition lowConfidenceFilter() {
        final double threshold = config.confidenceThreshold();
        return FilterDefinition.onAdd(
                "ai/low-confidence",
                "Applies ai/low-confidence label when AI agent confidence is below threshold ("
                        + threshold + "). Enables human reviewers to filter inbox for uncertain items.",
                config.lowConfidenceFilter().enabled(),
                "workItem.confidenceScore != null && workItem.confidenceScore < threshold",
                Map.of("threshold", threshold),
                List.of(ActionDescriptor.of("APPLY_LABEL",
                        Map.of("path", "ai/low-confidence", "appliedBy", "ai-confidence-gate"))));
    }
}
