package io.casehub.work.ai.skill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import io.casehub.work.api.SelectionContext;
import io.casehub.work.api.SkillProfile;

class EmbeddingSkillMatcherTest {

    private static Response<Embedding> resp(final float... values) {
        return Response.from(new Embedding(values));
    }

    private EmbeddingSkillMatcher matcher(final EmbeddingModel model) {
        return new EmbeddingSkillMatcher(model);
    }

    @Test
    void score_identicalVectors_returnsOne() {
        final EmbeddingModel model = mock(EmbeddingModel.class);
        when(model.embed(anyString())).thenReturn(resp(1f, 0f, 0f));

        final var ctx = new SelectionContext(null, null, null, null, null, "T", "D");
        final double score = matcher(model).score(SkillProfile.ofNarrative("expert"), ctx);
        assertThat(score).isCloseTo(1.0, within(0.001));
    }

    @Test
    void score_orthogonalVectors_returnsZero() {
        final EmbeddingModel model = mock(EmbeddingModel.class);
        // anyString() first, then specific override — specific wins in Mockito (last registered)
        when(model.embed(anyString())).thenReturn(resp(0f, 1f));
        when(model.embed("worker narrative")).thenReturn(resp(1f, 0f));

        final var profile = SkillProfile.ofNarrative("worker narrative");
        final var ctx = new SelectionContext("cat", null, "legal", null, null, "title", "desc");
        final double score = matcher(model).score(profile, ctx);
        assertThat(score).isCloseTo(0.0, within(0.001));
    }

    @Test
    void score_embeddingModelThrows_returnsNegativeOne() {
        final EmbeddingModel model = mock(EmbeddingModel.class);
        when(model.embed(anyString())).thenThrow(new RuntimeException("API down"));

        final var ctx = new SelectionContext(null, null, null, null, null, "T", "D");
        final double score = matcher(model).score(SkillProfile.ofNarrative("expert"), ctx);
        assertThat(score).isEqualTo(-1.0);
    }

    @Test
    void score_emptyNarrative_stillCallsModel() {
        final EmbeddingModel model = mock(EmbeddingModel.class);
        when(model.embed(anyString())).thenReturn(resp(0f, 0f, 1f));

        final var ctx = new SelectionContext(null, null, null, null, null, null, null);
        final double score = matcher(model).score(SkillProfile.ofNarrative(""), ctx);
        assertThat(score).isCloseTo(1.0, within(0.001));
    }

    @Test
    void score_zeroVectors_returnsZero() {
        final EmbeddingModel model = mock(EmbeddingModel.class);
        when(model.embed(anyString())).thenReturn(resp(0f, 0f, 0f));

        final var ctx = new SelectionContext(null, null, null, null, null, "T", null);
        final double score = matcher(model).score(SkillProfile.ofNarrative("text"), ctx);
        assertThat(score).isEqualTo(0.0);
    }

    @Test
    void score_requirementText_combinesNonNullContextFields() {
        final String[] capturedTexts = new String[2];
        final int[] callCount = { 0 };
        final EmbeddingModel model = mock(EmbeddingModel.class);
        // anyString() captures both embed calls in order: [0]=narrative, [1]=requirement
        when(model.embed(anyString())).thenAnswer(inv -> {
            capturedTexts[callCount[0]++] = inv.getArgument(0);
            return resp(1f, 0f);
        });

        final var ctx = new SelectionContext("contract", null, "legal", null, null,
                "Review NDA", null);
        matcher(model).score(SkillProfile.ofNarrative("worker"), ctx);

        // Requirement text should combine title + requiredCapabilities + category (non-null only)
        final String requirementText = capturedTexts[1]; // second embed call is for requirement
        assertThat(requirementText).contains("Review NDA");
        assertThat(requirementText).contains("legal");
        assertThat(requirementText).contains("contract");
    }
}
