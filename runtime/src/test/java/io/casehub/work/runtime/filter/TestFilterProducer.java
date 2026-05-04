package io.casehub.work.runtime.filter;

import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

/**
 * CDI producer for test filter definitions that exercise the filter registry REST API.
 * These definitions are referenced by name in PermanentFilterRegistryTest.
 */
@ApplicationScoped
class TestFilterProducer {

    @Produces
    FilterDefinition applyLabelFilter() {
        return FilterDefinition.onAdd("test/apply-label", "test apply-label filter", true,
                "workItem.score != null && workItem.score < 0.5",
                Map.of(),
                List.of(ActionDescriptor.of("APPLY_LABEL",
                        Map.of("path", "ai/test-label", "appliedBy", "test-filter"))));
    }

    @Produces
    FilterDefinition overrideGroupsFilter() {
        return FilterDefinition.onAdd("test/override-groups", "test override-groups filter", true,
                "workItem.score != null && workItem.score < 0.3",
                Map.of(),
                List.of(ActionDescriptor.of("OVERRIDE_CANDIDATE_GROUPS",
                        Map.of("groups", "review-team"))));
    }

    @Produces
    FilterDefinition setPriorityFilter() {
        return FilterDefinition.onAdd("test/set-priority", "test set-priority filter", true,
                "workItem.score != null && workItem.score < 0.15",
                Map.of(),
                List.of(ActionDescriptor.of("SET_PRIORITY",
                        Map.of("priority", "URGENT"))));
    }
}
