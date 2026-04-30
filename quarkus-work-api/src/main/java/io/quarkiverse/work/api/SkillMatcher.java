package io.casehub.work.api;

/**
 * SPI for scoring a worker's {@link SkillProfile} against a work item's
 * {@link SelectionContext}.
 *
 * <p>
 * Returns a score where higher = better match. The scale is implementation-defined
 * (e.g. cosine similarity ∈ [−1, 1], Jaccard ∈ [0, 1]). The configured threshold
 * must use the same scale as the active matcher.
 *
 * <p>
 * Implement as {@code @ApplicationScoped @Alternative @Priority(1)} to override
 * the built-in {@code EmbeddingSkillMatcher}.
 */
@FunctionalInterface
public interface SkillMatcher {

    /**
     * Score a worker's skill profile against a work item requirement.
     *
     * @param workerProfile the worker's skill description
     * @param context the work item's routing context
     * @return match score; higher is better. Return {@code -1.0} to signal failure.
     */
    double score(SkillProfile workerProfile, SelectionContext context);
}
