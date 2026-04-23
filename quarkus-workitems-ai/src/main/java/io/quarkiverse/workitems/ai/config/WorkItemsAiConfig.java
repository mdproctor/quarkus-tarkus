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
 * quarkus.workitems.ai.semantic.enabled=true
 * quarkus.workitems.ai.semantic.score-threshold=0.0
 * quarkus.workitems.ai.semantic.history-limit=50
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

    /**
     * Configuration for semantic skill matching.
     *
     * @return the semantic matching configuration group
     */
    SemanticConfig semantic();

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

    /**
     * Configuration for semantic skill matching via {@link io.quarkiverse.workitems.ai.skill.SemanticWorkerSelectionStrategy}.
     */
    interface SemanticConfig {
        /**
         * Whether semantic skill matching is active. When false, the strategy
         * returns {@code noChange()} immediately without scoring candidates.
         * Default: true.
         *
         * @return true if semantic matching should run
         */
        @WithDefault("true")
        boolean enabled();

        /**
         * Minimum cosine similarity score for a candidate to receive pre-assignment.
         * Candidates scoring at or below this threshold are excluded.
         * Default: 0.0 (any positive similarity accepted).
         *
         * @return the minimum score threshold
         */
        @WithName("score-threshold")
        @WithDefault("0.0")
        double scoreThreshold();

        /**
         * Maximum number of past completed WorkItems to consider when building
         * a resolution history skill profile. Most recent items are used first.
         * Default: 50.
         *
         * @return the history limit
         */
        @WithName("history-limit")
        @WithDefault("50")
        int historyLimit();
    }
}
