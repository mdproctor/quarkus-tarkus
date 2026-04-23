package io.quarkiverse.work.examples.semantic;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import io.quarkiverse.work.api.SelectionContext;
import io.quarkiverse.work.api.SkillMatcher;
import io.quarkiverse.work.api.SkillProfile;

/**
 * Deterministic {@link SkillMatcher} for examples — scores by shared keyword count.
 *
 * <p>
 * No embedding model required. Overrides {@code EmbeddingSkillMatcher} via
 * {@code @Alternative @Priority(2)} so the NDA review scenario runs headlessly
 * without any external AI provider configuration.
 *
 * <p>
 * Score = (shared keywords) / (context keyword count), ∈ [0, 1].
 * Common stop words are filtered before matching.
 */
@ApplicationScoped
@Alternative
@Priority(2)
public class KeywordSkillMatcher implements SkillMatcher {

    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "the", "in", "on", "at", "for", "and", "or", "of",
            "to", "with", "by", "is", "it", "as", "its", "from", "this", "that");

    @Override
    public double score(final SkillProfile workerProfile, final SelectionContext context) {
        if (workerProfile.narrative() == null || workerProfile.narrative().isBlank()) {
            return 0.0;
        }
        final Set<String> profileWords = tokenize(workerProfile.narrative());
        final Set<String> contextWords = tokenize(requirementText(context));
        if (contextWords.isEmpty()) {
            return 0.0;
        }
        final long matches = contextWords.stream().filter(profileWords::contains).count();
        return (double) matches / contextWords.size();
    }

    private String requirementText(final SelectionContext ctx) {
        return Stream.of(ctx.title(), ctx.description(), ctx.category())
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.joining(" "));
    }

    private Set<String> tokenize(final String text) {
        return Arrays.stream(text.toLowerCase().split("[^a-z0-9]+"))
                .filter(w -> !w.isEmpty() && !STOP_WORDS.contains(w))
                .collect(Collectors.toSet());
    }
}
