package io.quarkiverse.work.ai.skill;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkiverse.work.api.SelectionContext;
import io.quarkiverse.work.api.SkillMatcher;
import io.quarkiverse.work.api.SkillProfile;
import io.quarkiverse.work.api.SkillProfileProvider;
import io.quarkiverse.work.api.WorkerCandidate;

class SemanticStrategyTest {

    private SelectionContext ctx;

    @BeforeEach
    void setUp() {
        ctx = new SelectionContext("legal", "HIGH", null, null, "alice,bob",
                "Review NDA", "Review the NDA for Acme Corp.");
    }

    private WorkerCandidate candidate(final String id) {
        return new WorkerCandidate(id, Set.of(), 0);
    }

    private SemanticWorkerSelectionStrategy strategy(
            final SkillProfileProvider provider, final SkillMatcher matcher,
            final boolean enabled, final double threshold) {
        return new SemanticWorkerSelectionStrategy(provider, matcher,
                new io.quarkiverse.work.core.strategy.LeastLoadedStrategy(), enabled, threshold);
    }

    @Test
    void selectsHighestScoringCandidate() {
        final SkillProfileProvider provider = (id, caps) -> SkillProfile.ofNarrative(id);
        final SkillMatcher matcher = (profile, c) -> profile.narrative().equals("alice") ? 0.9 : 0.3;

        final var result = strategy(provider, matcher, true, 0.0)
                .select(ctx, List.of(candidate("alice"), candidate("bob")));

        assertThat(result.assigneeId()).isEqualTo("alice");
    }

    @Test
    void allBelowThreshold_fallsBackToLeastLoaded() {
        final SkillProfileProvider provider = (id, caps) -> SkillProfile.ofNarrative(id);
        final SkillMatcher matcher = (profile, c) -> -0.5;

        final var result = strategy(provider, matcher, true, 0.0)
                .select(ctx, List.of(candidate("alice"), candidate("bob")));

        // LeastLoadedStrategy assigns to one of the candidates (both at count=0)
        assertThat(result.assigneeId()).isNotNull();
    }

    @Test
    void emptyCandidates_returnsNoChange() {
        final SkillProfileProvider provider = (id, caps) -> SkillProfile.ofNarrative(id);
        final SkillMatcher matcher = (profile, c) -> 0.9;

        final var result = strategy(provider, matcher, true, 0.0)
                .select(ctx, List.of());

        // LeastLoadedStrategy also returns noChange() for empty candidates
        assertThat(result.isNoOp()).isTrue();
    }

    @Test
    void disabled_fallsBackToLeastLoaded() {
        // When disabled, semantic scoring is skipped entirely — LeastLoaded takes over
        final SkillMatcher matcher = (profile, c) -> {
            throw new RuntimeException("should not be called");
        };

        final var result = strategy((id, caps) -> SkillProfile.ofNarrative(""), matcher, false, 0.0)
                .select(ctx, List.of(candidate("alice")));

        assertThat(result.assigneeId()).isEqualTo("alice");
    }

    @Test
    void matcherThrows_fallsBackToLeastLoaded() {
        final SkillProfileProvider provider = (id, caps) -> SkillProfile.ofNarrative(id);
        final SkillMatcher matcher = (profile, c) -> {
            throw new RuntimeException("model down");
        };

        final var result = strategy(provider, matcher, true, 0.0)
                .select(ctx, List.of(candidate("alice")));

        assertThat(result.assigneeId()).isEqualTo("alice");
    }

    @Test
    void thresholdFiltersOutLowScorers() {
        final SkillProfileProvider provider = (id, caps) -> SkillProfile.ofNarrative(id);
        final SkillMatcher matcher = (profile, c) -> profile.narrative().equals("alice") ? 0.5 : 0.8;

        final var result = strategy(provider, matcher, true, 0.6)
                .select(ctx, List.of(candidate("alice"), candidate("bob")));

        assertThat(result.assigneeId()).isEqualTo("bob");
    }

    @Test
    void singleCandidate_aboveThreshold_assigned() {
        final SkillProfileProvider provider = (id, caps) -> SkillProfile.ofNarrative(id);
        final SkillMatcher matcher = (profile, c) -> 0.7;

        final var result = strategy(provider, matcher, true, 0.5)
                .select(ctx, List.of(candidate("alice")));

        assertThat(result.assigneeId()).isEqualTo("alice");
    }

    @Test
    void singleCandidate_belowThreshold_fallsBackToLeastLoaded() {
        final SkillProfileProvider provider = (id, caps) -> SkillProfile.ofNarrative(id);
        final SkillMatcher matcher = (profile, c) -> 0.3;

        final var result = strategy(provider, matcher, true, 0.5)
                .select(ctx, List.of(candidate("alice")));

        assertThat(result.assigneeId()).isEqualTo("alice");
    }
}
