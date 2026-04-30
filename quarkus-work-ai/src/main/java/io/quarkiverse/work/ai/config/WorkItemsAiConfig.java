package io.casehub.work.ai.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

/**
 * Configuration for the quarkus-work-ai module.
 *
 * <pre>
 * casehub.work.ai.confidence-threshold=0.7
 * casehub.work.ai.low-confidence-filter.enabled=true
 * casehub.work.ai.semantic.enabled=true
 * casehub.work.ai.semantic.score-threshold=0.0
 * casehub.work.ai.semantic.history-limit=50
 * casehub.work.ai.suggestion.history-limit=5
 * casehub.work.ai.escalation-summary.enabled=true
 * casehub.work.ai.escalation-summary.audit-limit=10
 * </pre>
 */
@ConfigMapping(prefix = "casehub.work.ai")
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

    /**
     * Configuration for AI-assisted resolution suggestions.
     *
     * @return the suggestion configuration group
     */
    SuggestionConfig suggestion();

    /**
     * Configuration for LLM-generated escalation summaries.
     *
     * @return the escalation summary configuration group
     */
    @WithName("escalation-summary")
    EscalationSummaryConfig escalationSummary();

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
     * Configuration for semantic skill matching via {@link io.casehub.work.ai.skill.SemanticWorkerSelectionStrategy}.
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

    /** Configuration for AI-assisted resolution suggestions. */
    interface SuggestionConfig {

        /**
         * Maximum number of past completed WorkItems to include as few-shot examples
         * in the resolution suggestion prompt. Most recently completed items are used first.
         * Default: 5.
         *
         * @return the history limit
         */
        @WithName("history-limit")
        @WithDefault("5")
        int historyLimit();
    }

    /** Configuration for LLM-generated escalation summaries. */
    interface EscalationSummaryConfig {

        /**
         * Whether escalation summarisation is active. When false, no summary is generated
         * even if a {@code ChatModel} is configured.
         * Default: true.
         *
         * @return true if summaries should be generated on escalation events
         */
        @WithDefault("true")
        boolean enabled();

        /**
         * Maximum number of audit entries included in the escalation summary prompt.
         * Most recent entries are used first.
         * Default: 10.
         *
         * @return the audit entry limit
         */
        @WithName("audit-limit")
        @WithDefault("10")
        int auditLimit();
    }
}
