package io.quarkiverse.work.core.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

class JexlConditionEvaluatorTest {

    private final JexlConditionEvaluator eval = new JexlConditionEvaluator();

    @Test
    void evaluate_trueWhenConditionMatches() {
        final var ctx = Map.<String, Object> of("category", "finance", "priority", "HIGH");
        assertThat(eval.evaluate("workItem.category == 'finance'", Map.of(), ctx)).isTrue();
    }

    @Test
    void evaluate_falseWhenConditionDoesNotMatch() {
        final var ctx = Map.<String, Object> of("category", "legal");
        assertThat(eval.evaluate("workItem.category == 'finance'", Map.of(), ctx)).isFalse();
    }

    @Test
    void evaluate_conditionContextVariablesMerged() {
        final var ctx = Map.<String, Object> of("score", 0.5);
        final var condCtx = Map.<String, Object> of("threshold", 0.7);
        assertThat(eval.evaluate("workItem.score < threshold", condCtx, ctx)).isTrue();
    }

    @Test
    void evaluate_blankCondition_returnsFalse() {
        assertThat(eval.evaluate("", Map.of(), Map.of())).isFalse();
        assertThat(eval.evaluate(null, Map.of(), Map.of())).isFalse();
    }

    @Test
    void evaluate_invalidJexl_returnsFalse() {
        assertThat(eval.evaluate("!!!invalid!!!", Map.of(), Map.of())).isFalse();
    }

    @Test
    void evaluate_nullConditionContext_doesNotThrow() {
        final var ctx = Map.<String, Object> of("category", "finance");
        assertThat(eval.evaluate("workItem.category == 'finance'", null, ctx)).isTrue();
    }
}
