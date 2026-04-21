package io.quarkiverse.workitems.filterregistry.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkiverse.workitems.runtime.model.WorkItem;
import io.quarkiverse.workitems.runtime.model.WorkItemPriority;

class JexlConditionEvaluatorTest {

    private final JexlConditionEvaluator evaluator = new JexlConditionEvaluator();

    @Test
    void evaluate_returnsTrue_whenConditionMatches() {
        final WorkItem wi = workItem(0.55, "finance");
        assertThat(evaluator.evaluate(
                "workItem.confidenceScore != null && workItem.confidenceScore < threshold",
                Map.of("threshold", 0.7), wi)).isTrue();
    }

    @Test
    void evaluate_returnsFalse_whenConfidenceAboveThreshold() {
        final WorkItem wi = workItem(0.85, "finance");
        assertThat(evaluator.evaluate(
                "workItem.confidenceScore != null && workItem.confidenceScore < threshold",
                Map.of("threshold", 0.7), wi)).isFalse();
    }

    @Test
    void evaluate_returnsFalse_whenConfidenceIsNull() {
        final WorkItem wi = workItem(null, "finance");
        assertThat(evaluator.evaluate(
                "workItem.confidenceScore != null && workItem.confidenceScore < threshold",
                Map.of("threshold", 0.7), wi)).isFalse();
    }

    @Test
    void evaluate_returnsTrue_forCategoryCondition() {
        final WorkItem wi = workItem(null, "finance");
        assertThat(evaluator.evaluate(
                "workItem.category == 'finance'", Map.of(), wi)).isTrue();
    }

    @Test
    void evaluate_returnsFalse_forNonMatchingCategory() {
        final WorkItem wi = workItem(null, "hr");
        assertThat(evaluator.evaluate(
                "workItem.category == 'finance'", Map.of(), wi)).isFalse();
    }

    @Test
    void evaluate_returnsFalse_forInvalidExpression() {
        final WorkItem wi = workItem(0.5, "cat");
        assertThat(evaluator.evaluate("!!!invalid{[}", Map.of(), wi)).isFalse();
    }

    @Test
    void evaluate_returnsFalse_forBlankCondition() {
        assertThat(evaluator.evaluate("", Map.of(), new WorkItem())).isFalse();
    }

    @Test
    void evaluate_exposesConditionContextVariables() {
        final WorkItem wi = workItem(null, "loan");
        assertThat(evaluator.evaluate(
                "myVar == 42", Map.of("myVar", 42), wi)).isTrue();
    }

    @Test
    void evaluate_priorityCondition_usingEnumName() {
        final WorkItem wi = new WorkItem();
        wi.priority = WorkItemPriority.HIGH;
        assertThat(evaluator.evaluate(
                "workItem.priority.name() == 'HIGH'", Map.of(), wi)).isTrue();
    }

    @Test
    void toMap_containsAllPublicNonStaticWorkItemFields() {
        // Protects against toMap() going stale when WorkItem gains new fields.
        // If this test fails, update JexlConditionEvaluator.toMap() to include the new field.
        final long publicNonStaticFieldCount = Arrays.stream(WorkItem.class.getFields())
                .filter(f -> !java.lang.reflect.Modifier.isStatic(f.getModifiers()))
                .count();
        final java.util.Map<String, Object> map = JexlConditionEvaluator.toMap(new WorkItem());
        assertThat(map).hasSize((int) publicNonStaticFieldCount);
    }

    private WorkItem workItem(final Double confidenceScore, final String category) {
        final WorkItem wi = new WorkItem();
        wi.confidenceScore = confidenceScore;
        wi.category = category;
        return wi;
    }
}
