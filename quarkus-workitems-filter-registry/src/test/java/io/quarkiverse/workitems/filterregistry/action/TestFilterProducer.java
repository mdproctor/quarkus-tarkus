package io.quarkiverse.workitems.filterregistry.action;

import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import io.quarkiverse.workitems.filterregistry.spi.ActionDescriptor;
import io.quarkiverse.workitems.filterregistry.spi.FilterDefinition;

/** CDI producer for test filter definitions that exercise each built-in action. */
@ApplicationScoped
class TestFilterProducer {

    @Produces
    FilterDefinition applyLabelFilter() {
        return FilterDefinition.onAdd("test/apply-label", "test", true,
                "workItem.confidenceScore != null && workItem.confidenceScore < 0.5",
                Map.of(),
                List.of(ActionDescriptor.of("APPLY_LABEL",
                        Map.of("path", "ai/test-label", "appliedBy", "test-filter"))));
    }

    @Produces
    FilterDefinition overrideGroupsFilter() {
        return FilterDefinition.onAdd("test/override-groups", "test", true,
                "workItem.confidenceScore != null && workItem.confidenceScore < 0.3",
                Map.of(),
                List.of(ActionDescriptor.of("OVERRIDE_CANDIDATE_GROUPS",
                        Map.of("groups", "review-team"))));
    }

    @Produces
    FilterDefinition setPriorityFilter() {
        return FilterDefinition.onAdd("test/set-priority", "test", true,
                "workItem.confidenceScore != null && workItem.confidenceScore < 0.15",
                Map.of(),
                List.of(ActionDescriptor.of("SET_PRIORITY",
                        Map.of("priority", "CRITICAL"))));
    }
}
