package io.casehub.work.queues.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.casehub.work.queues.model.FilterAction;
import io.casehub.work.runtime.model.*;

/**
 * Unit tests for the core re-evaluation algorithm (no Quarkus, no DB).
 * Uses the static helper methods that mirror FilterEngineImpl logic.
 */
class FilterEngineTest {

    record TestFilter(String conditionKey, List<FilterAction> actions) {
    }

    // Simulates the multi-pass re-evaluation loop
    static void evaluate(WorkItem wi, List<TestFilter> filters) {
        wi.labels.removeIf(l -> l.persistence == LabelPersistence.INFERRED);
        boolean changed = true;
        int passes = 0;
        while (changed && passes < 10) {
            changed = false;
            passes++;
            for (var f : filters) {
                if (matches(f.conditionKey(), wi)) {
                    for (var a : f.actions()) {
                        if ("APPLY_LABEL".equals(a.type())) {
                            boolean exists = wi.labels.stream().anyMatch(l -> l.path.equals(a.labelPath()));
                            if (!exists) {
                                wi.labels.add(new WorkItemLabel(a.labelPath(), LabelPersistence.INFERRED, "test"));
                                changed = true;
                            }
                        }
                    }
                }
            }
        }
    }

    static boolean matches(String key, WorkItem wi) {
        return switch (key) {
            case "HIGH" -> wi.priority == WorkItemPriority.HIGH;
            case "PENDING" -> wi.status == WorkItemStatus.PENDING;
            case "HAS_INTAKE" -> wi.labels.stream().anyMatch(l -> l.path.equals("intake"));
            case "ALWAYS" -> true;
            default -> false;
        };
    }

    @Test
    void matchingFilter_appliesInferredLabel() {
        var wi = new WorkItem();
        wi.priority = WorkItemPriority.HIGH;
        wi.status = WorkItemStatus.PENDING;
        evaluate(wi, List.of(new TestFilter("HIGH", List.of(FilterAction.applyLabel("priority/high")))));
        assertThat(wi.labels).extracting(l -> l.path).contains("priority/high");
        assertThat(wi.labels).filteredOn(l -> l.path.equals("priority/high"))
                .extracting(l -> l.persistence).containsOnly(LabelPersistence.INFERRED);
    }

    @Test
    void nonMatchingFilter_doesNotApplyLabel() {
        var wi = new WorkItem();
        wi.priority = WorkItemPriority.MEDIUM;
        evaluate(wi, List.of(new TestFilter("HIGH", List.of(FilterAction.applyLabel("priority/high")))));
        assertThat(wi.labels).extracting(l -> l.path).doesNotContain("priority/high");
    }

    @Test
    void stripsExistingInferredLabels_beforeEval() {
        var wi = new WorkItem();
        wi.priority = WorkItemPriority.MEDIUM;
        wi.labels.add(new WorkItemLabel("old/inferred", LabelPersistence.INFERRED, "old"));
        wi.labels.add(new WorkItemLabel("manual/keep", LabelPersistence.MANUAL, "alice"));
        evaluate(wi, List.of());
        assertThat(wi.labels).extracting(l -> l.path).doesNotContain("old/inferred");
        assertThat(wi.labels).extracting(l -> l.path).contains("manual/keep");
    }

    @Test
    void propagationChain_filterBSeesLabelFromA() {
        var wi = new WorkItem();
        wi.priority = WorkItemPriority.HIGH;
        var filterA = new TestFilter("HIGH", List.of(FilterAction.applyLabel("intake")));
        var filterB = new TestFilter("HAS_INTAKE", List.of(FilterAction.applyLabel("intake/triage")));
        evaluate(wi, List.of(filterA, filterB));
        assertThat(wi.labels).extracting(l -> l.path).contains("intake", "intake/triage");
    }

    @Test
    void circularTerminates_maxPasses() {
        var wi = new WorkItem();
        var filterA = new TestFilter("ALWAYS", List.of(FilterAction.applyLabel("cycle/a")));
        var filterB = new TestFilter("ALWAYS", List.of(FilterAction.applyLabel("cycle/b")));
        evaluate(wi, List.of(filterA, filterB));
        assertThat(wi.labels).extracting(l -> l.path).contains("cycle/a", "cycle/b");
    }

    @Test
    void multipleFilters_allApplied() {
        var wi = new WorkItem();
        wi.priority = WorkItemPriority.HIGH;
        wi.status = WorkItemStatus.PENDING;
        evaluate(wi, List.of(
                new TestFilter("HIGH", List.of(FilterAction.applyLabel("priority/high"))),
                new TestFilter("PENDING", List.of(FilterAction.applyLabel("intake")))));
        assertThat(wi.labels).extracting(l -> l.path).contains("priority/high", "intake");
    }
}
