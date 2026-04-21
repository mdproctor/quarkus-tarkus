package io.quarkiverse.workitems.ai.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

/**
 * Configuration for the quarkus-workitems-ai module.
 *
 * <pre>
 * quarkus.workitems.ai.confidence-threshold=0.7
 * quarkus.workitems.ai.low-confidence-filter.enabled=true
 * </pre>
 */
@ConfigMapping(prefix = "quarkus.workitems.ai")
public interface WorkItemsAiConfig {

    /**
     * Confidence threshold below which a WorkItem is considered low-confidence.
     * WorkItems with {@code confidenceScore} strictly less than this value receive
     * the {@code ai/low-confidence} label automatically.
     * Default: 0.7.
     *
     * @return the threshold value (0.0–1.0)
     */
    @WithDefault("0.7")
    double confidenceThreshold();

    /**
     * Configuration for the low-confidence routing filter.
     *
     * @return the low-confidence filter configuration group
     */
    @WithName("low-confidence-filter")
    LowConfidenceFilterConfig lowConfidenceFilter();

    /** Configuration for the low-confidence routing filter. */
    interface LowConfidenceFilterConfig {
        /**
         * Whether the low-confidence filter is active. When false, no
         * {@code ai/low-confidence} label is applied regardless of score.
         * Default: true.
         *
         * @return true if the filter should fire on WorkItem creation
         */
        @WithDefault("true")
        boolean enabled();
    }
}
