package io.quarkiverse.workitems.queues.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkiverse.workitems.runtime.model.WorkItem;
import io.quarkiverse.workitems.runtime.model.WorkItemPriority;
import io.quarkiverse.workitems.runtime.model.WorkItemStatus;

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
        assertThat(evaluator.evaluate(wi, ".priority == \"HIGH\"")).isTrue();
    }

    @Test
    void evaluate_priorityHigh_notNormal() {
        var wi = wi(WorkItemStatus.PENDING, WorkItemPriority.NORMAL, null);
        assertThat(evaluator.evaluate(wi, ".priority == \"HIGH\"")).isFalse();
    }

    @Test
    void evaluate_statusPending() {
        var wi = wi(WorkItemStatus.PENDING, WorkItemPriority.NORMAL, null);
        assertThat(evaluator.evaluate(wi, ".status == \"PENDING\"")).isTrue();
    }

    @Test
    void evaluate_assigneeNull() {
        var wi = wi(WorkItemStatus.PENDING, WorkItemPriority.NORMAL, null);
        assertThat(evaluator.evaluate(wi, ".assigneeId == null")).isTrue();
    }

    @Test
    void evaluate_malformed_returnsFalse() {
        var wi = wi(WorkItemStatus.PENDING, WorkItemPriority.NORMAL, null);
        assertThat(evaluator.evaluate(wi, "not valid jq @@@")).isFalse();
    }

    private WorkItem wi(final WorkItemStatus s, final WorkItemPriority p, final String a) {
        var wi = new WorkItem();
        wi.status = s;
        wi.priority = p;
        wi.assigneeId = a;
        return wi;
    }
}
