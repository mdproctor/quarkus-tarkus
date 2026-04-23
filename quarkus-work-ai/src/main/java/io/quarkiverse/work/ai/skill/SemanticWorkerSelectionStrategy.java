package io.quarkiverse.work.ai.skill;

import java.util.Comparator;
import java.util.List;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import io.quarkiverse.work.ai.config.WorkItemsAiConfig;
import io.quarkiverse.work.api.AssignmentDecision;
import io.quarkiverse.work.api.SelectionContext;
import io.quarkiverse.work.api.SkillMatcher;
import io.quarkiverse.work.api.SkillProfile;
import io.quarkiverse.work.api.SkillProfileProvider;
import io.quarkiverse.work.api.WorkerCandidate;
import io.quarkiverse.work.api.WorkerSelectionStrategy;
import io.quarkiverse.work.core.strategy.LeastLoadedStrategy;

/**
 * Assigns work to the candidate whose skill profile best matches the work item's
 * semantic content.
 *
 * <p>
 * Auto-activates when {@code quarkus-work-ai} is on the classpath —
 * {@code @Alternative @Priority(1)} overrides the config-selected built-in strategy
 * without requiring a beans.xml entry.
 *
 * <p>
 * When disabled ({@code quarkus.work.ai.semantic.enabled=false}) or when
 * all candidates score below the threshold, falls back to {@link LeastLoadedStrategy}
 * so workload-aware routing still fires even when AI is unavailable.
 */
@ApplicationScoped
@Alternative
@Priority(1)
public class SemanticWorkerSelectionStrategy implements WorkerSelectionStrategy {

    private static final Logger LOG = Logger.getLogger(SemanticWorkerSelectionStrategy.class);

    private final SkillProfileProvider profileProvider;
    private final SkillMatcher matcher;
    private final LeastLoadedStrategy fallback;
    private final boolean enabled;
    private final double scoreThreshold;

    @Inject
    public SemanticWorkerSelectionStrategy(
            final SkillProfileProvider profileProvider,
            final SkillMatcher matcher,
            final LeastLoadedStrategy fallback,
            final WorkItemsAiConfig config) {
        this.profileProvider = profileProvider;
        this.matcher = matcher;
        this.fallback = fallback;
        this.enabled = config.semantic().enabled();
        this.scoreThreshold = config.semantic().scoreThreshold();
    }

    /** Package-private constructor for unit tests — bypasses CDI and config. */
    SemanticWorkerSelectionStrategy(final SkillProfileProvider profileProvider,
            final SkillMatcher matcher, final LeastLoadedStrategy fallback,
            final boolean enabled, final double scoreThreshold) {
        this.profileProvider = profileProvider;
        this.matcher = matcher;
        this.fallback = fallback;
        this.enabled = enabled;
        this.scoreThreshold = scoreThreshold;
    }

    @Override
    public AssignmentDecision select(final SelectionContext context,
            final List<WorkerCandidate> candidates) {
        if (!enabled || candidates.isEmpty()) {
            return fallback.select(context, candidates);
        }
        try {
            return candidates.stream()
                    .map(c -> {
                        final SkillProfile profile = profileProvider.getProfile(
                                c.id(), c.capabilities());
                        final double score = matcher.score(profile, context);
                        return new CandidateScore(c, score);
                    })
                    .filter(cs -> cs.score > scoreThreshold)
                    .max(Comparator.comparingDouble(cs -> cs.score))
                    .map(cs -> AssignmentDecision.assignTo(cs.candidate.id()))
                    .orElseGet(() -> {
                        LOG.warnf("SemanticWorkerSelectionStrategy: no candidate scored above "
                                + "threshold %.2f — falling back to LeastLoadedStrategy",
                                scoreThreshold);
                        return fallback.select(context, candidates);
                    });
        } catch (final Exception e) {
            LOG.warnf("SemanticWorkerSelectionStrategy failed: %s — falling back to "
                    + "LeastLoadedStrategy", e.getMessage());
            return fallback.select(context, candidates);
        }
    }

    private record CandidateScore(WorkerCandidate candidate, double score) {
    }
}
