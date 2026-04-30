package io.casehub.work.queues.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.work.runtime.model.LabelPersistence;
import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.runtime.model.WorkItemLabel;
import io.casehub.work.runtime.model.WorkItemPriority;
import io.casehub.work.runtime.model.WorkItemStatus;

class JqConditionEvaluatorTest {
    private JqConditionEvaluator evaluator;

    @BeforeEach
    void setup() {
        evaluator = new JqConditionEvaluator();
    }

    @Test
    void language_isJq() {
        assertThat(evaluator.language()).isEqualTo("jq");
    }

    @Test
    void evaluate_priorityHigh_matchesHigh() {
        var wi = wi(WorkItemStatus.PENDING, WorkItemPriority.HIGH, null);
        assertThat(evaluator.evaluate(wi, ExpressionDescriptor.of(evaluator.language(), ".priority == \"HIGH\""))).isTrue();
    }

    @Test
    void evaluate_priorityHigh_notNormal() {
        var wi = wi(WorkItemStatus.PENDING, WorkItemPriority.NORMAL, null);
        assertThat(evaluator.evaluate(wi, ExpressionDescriptor.of(evaluator.language(), ".priority == \"HIGH\""))).isFalse();
    }

    @Test
    void evaluate_statusPending() {
        var wi = wi(WorkItemStatus.PENDING, WorkItemPriority.NORMAL, null);
        assertThat(evaluator.evaluate(wi, ExpressionDescriptor.of(evaluator.language(), ".status == \"PENDING\""))).isTrue();
    }

    @Test
    void evaluate_assigneeNull() {
        var wi = wi(WorkItemStatus.PENDING, WorkItemPriority.NORMAL, null);
        assertThat(evaluator.evaluate(wi, ExpressionDescriptor.of(evaluator.language(), ".assigneeId == null"))).isTrue();
    }

    @Test
    void evaluate_malformed_returnsFalse() {
        var wi = wi(WorkItemStatus.PENDING, WorkItemPriority.NORMAL, null);
        assertThat(evaluator.evaluate(wi, ExpressionDescriptor.of(evaluator.language(), "not valid jq @@@"))).isFalse();
    }

    @Test
    void evaluate_labelCheck_matchesWorkItemWithLabel() {
        var wi = wi(WorkItemStatus.PENDING, WorkItemPriority.NORMAL, null);
        wi.labels.add(new WorkItemLabel("legal/contracts", LabelPersistence.MANUAL, "alice"));
        assertThat(evaluator.evaluate(wi,
                ExpressionDescriptor.of(evaluator.language(), ".labels | contains([\"legal/contracts\"])"))).isTrue();
    }

    private WorkItem wi(final WorkItemStatus s, final WorkItemPriority p, final String a) {
        var wi = new WorkItem();
        wi.status = s;
        wi.priority = p;
        wi.assigneeId = a;
        return wi;
    }
}
