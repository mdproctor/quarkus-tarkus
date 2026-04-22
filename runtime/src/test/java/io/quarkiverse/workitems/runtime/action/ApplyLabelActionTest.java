package io.quarkiverse.workitems.runtime.action;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.quarkiverse.workitems.runtime.model.LabelPersistence;
import io.quarkiverse.workitems.runtime.model.WorkItem;

class ApplyLabelActionTest {

    private final ApplyLabelAction action = new ApplyLabelAction();

    private WorkItem workItem() {
        final var wi = new WorkItem();
        wi.id = UUID.randomUUID();
        wi.labels = new ArrayList<>();
        return wi;
    }

    @Test
    void type_isAPPLY_LABEL() {
        assertThat(action.type()).isEqualTo("APPLY_LABEL");
    }

    @Test
    void apply_addsInferredLabel() {
        final var wi = workItem();
        action.apply(wi, Map.of("path", "legal/contracts"));
        assertThat(wi.labels).hasSize(1);
        assertThat(wi.labels.get(0).path).isEqualTo("legal/contracts");
        assertThat(wi.labels.get(0).persistence).isEqualTo(LabelPersistence.INFERRED);
    }

    @Test
    void apply_idempotent_doesNotAddDuplicateInferred() {
        final var wi = workItem();
        action.apply(wi, Map.of("path", "legal/contracts"));
        action.apply(wi, Map.of("path", "legal/contracts"));
        assertThat(wi.labels).hasSize(1);
    }

    @Test
    void apply_blankPath_ignored() {
        final var wi = workItem();
        action.apply(wi, Map.of("path", "  "));
        assertThat(wi.labels).isEmpty();
    }

    @Test
    void apply_missingPath_ignored() {
        final var wi = workItem();
        action.apply(wi, Map.of());
        assertThat(wi.labels).isEmpty();
    }

    @Test
    void apply_customAppliedBy() {
        final var wi = workItem();
        action.apply(wi, Map.of("path", "ai/tag", "appliedBy", "my-filter"));
        assertThat(wi.labels.get(0).appliedBy).isEqualTo("my-filter");
    }

    @Test
    void apply_defaultAppliedBy_isFilterRegistry() {
        final var wi = workItem();
        action.apply(wi, Map.of("path", "legal/contracts"));
        assertThat(wi.labels.get(0).appliedBy).isEqualTo("filter-registry");
    }
}
