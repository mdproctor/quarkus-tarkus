package io.quarkiverse.workitems.runtime.action;

import java.util.ArrayList;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.work.core.filter.FilterAction;
import io.quarkiverse.workitems.runtime.model.LabelPersistence;
import io.quarkiverse.workitems.runtime.model.WorkItem;
import io.quarkiverse.workitems.runtime.model.WorkItemLabel;

/**
 * Built-in FilterAction that adds an INFERRED label to a WorkItem.
 *
 * <p>
 * Params: {@code path} (required) — the label path to add;
 * {@code appliedBy} (optional, default "filter-registry") — the filter identity.
 *
 * <p>
 * Skips silently if {@code path} is blank or if the label is already present with
 * INFERRED persistence.
 */
@ApplicationScoped
public class ApplyLabelAction implements FilterAction {

    @Override
    public String type() {
        return "APPLY_LABEL";
    }

    @Override
    public void apply(final Object workUnit, final Map<String, Object> params) {
        final WorkItem workItem = (WorkItem) workUnit;
        final Object pathParam = params.get("path");
        if (pathParam == null || pathParam.toString().isBlank()) {
            return;
        }
        final String path = pathParam.toString();
        final Object appliedByParam = params.get("appliedBy");
        final String appliedBy = (appliedByParam != null && !appliedByParam.toString().isBlank())
                ? appliedByParam.toString()
                : "filter-registry";

        if (workItem.labels == null) {
            workItem.labels = new ArrayList<>();
        }
        // Deduplication checks path+persistence only — appliedBy is intentionally excluded
        // so the same label path is never added twice, regardless of which filter applied it
        if (workItem.labels.stream()
                .anyMatch(l -> path.equals(l.path) && l.persistence == LabelPersistence.INFERRED)) {
            return; // already present — idempotent
        }

        workItem.labels.add(new WorkItemLabel(path, LabelPersistence.INFERRED, appliedBy));
        // No put() — the @ElementCollection mutation is flushed by the outer transaction at commit
    }
}
