package io.quarkiverse.workitems.filterregistry.action;

import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.workitems.filterregistry.spi.FilterAction;
import io.quarkiverse.workitems.runtime.model.WorkItem;

/**
 * Built-in FilterAction that replaces a WorkItem's {@code candidateGroups} field.
 *
 * <p>
 * Params: {@code groups} (required) — the replacement candidate groups value.
 * Skips silently if {@code groups} is null.
 */
@ApplicationScoped
public class OverrideCandidateGroupsAction implements FilterAction {

    @Override
    public String type() {
        return "OVERRIDE_CANDIDATE_GROUPS";
    }

    @Override
    public void apply(final WorkItem workItem, final Map<String, Object> params) {
        final Object groupsParam = params.get("groups");
        if (groupsParam == null) {
            return;
        }
        workItem.candidateGroups = groupsParam.toString();
        // No put() — outer transaction flushes the dirty entity at commit
    }
}
