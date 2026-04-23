package io.quarkiverse.workitems.ai.skill;

import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import dev.langchain4j.model.embedding.EmbeddingModel;
import io.quarkiverse.work.api.SelectionContext;
import io.quarkiverse.work.api.SkillMatcher;
import io.quarkiverse.work.api.SkillProfile;

/**
 * Scores a worker's skill narrative against a work item using cosine similarity of embeddings.
 *
 * <p>
 * Requires an {@link EmbeddingModel} CDI bean — typically provided by a
 * {@code quarkus-langchain4j-*} provider extension configured by the consuming app.
 * When no model is available, returns {@code -1.0} (treated as below threshold).
 *
 * <p>
 * On any embedding exception, returns {@code -1.0} so the strategy treats the candidate
 * as below threshold and falls back to {@code noChange()}.
 */
@ApplicationScoped
public class EmbeddingSkillMatcher implements SkillMatcher {

    private static final Logger LOG = Logger.getLogger(EmbeddingSkillMatcher.class);

    private final EmbeddingModel embeddingModel;

    @Inject
    public EmbeddingSkillMatcher(final Instance<EmbeddingModel> embeddingModelInstance) {
        this.embeddingModel = embeddingModelInstance.isResolvable()
                ? embeddingModelInstance.get()
                : null;
    }

    /** Test constructor — inject model directly without CDI Instance wrapper. */
    EmbeddingSkillMatcher(final EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    @Override
    public double score(final SkillProfile workerProfile, final SelectionContext context) {
        if (embeddingModel == null) {
            LOG.warn("No EmbeddingModel available — returning -1.0. "
                    + "Configure a langchain4j provider to enable semantic matching.");
            return -1.0;
        }
        try {
            final float[] workerVec = embeddingModel
                    .embed(workerProfile.narrative() != null ? workerProfile.narrative() : "")
                    .content().vector();
            final float[] requirementVec = embeddingModel
                    .embed(requirementText(context))
                    .content().vector();
            return cosineSimilarity(workerVec, requirementVec);
        } catch (final Exception e) {
            LOG.warnf("EmbeddingModel failed — returning -1.0 for candidate scoring: %s",
                    e.getMessage());
            return -1.0;
        }
    }

    private String requirementText(final SelectionContext ctx) {
        return Stream.of(ctx.title(), ctx.description(), ctx.requiredCapabilities(), ctx.category())
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.joining(" "));
    }

    private double cosineSimilarity(final float[] a, final float[] b) {
        if (a.length != b.length || a.length == 0) {
            return 0.0;
        }
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) {
            return 0.0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
