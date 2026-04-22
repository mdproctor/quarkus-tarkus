package io.quarkiverse.workitems.runtime.action;

import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.work.core.filter.FilterAction;
import io.quarkiverse.workitems.runtime.model.WorkItem;

/**
 * Built-in FilterAction that replaces a WorkItem's {@code candidateGroups} field.
 *
 * <p>
 * Params: {@code groups} (required) — the replacement candidate groups value.
 * Skips silently if {@code groups} is null or blank.
 */
@ApplicationScoped
public class OverrideCandidateGroupsAction implements FilterAction {

    @Override
    public String type() {
        return "OVERRIDE_CANDIDATE_GROUPS";
    }

    @Override
    public void apply(final Object workUnit, final Map<String, Object> params) {
        final WorkItem workItem = (WorkItem) workUnit;
        final Object groupsParam = params.get("groups");
        if (groupsParam == null) {
            return;
        }
        final String groups = groupsParam.toString();
        if (groups.isBlank()) {
            return;
        }
        workItem.candidateGroups = groups;
        // No put() — outer transaction flushes the dirty entity at commit
    }
}
