package io.quarkiverse.work.runtime.action;

import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.work.core.filter.FilterAction;
import io.quarkiverse.work.runtime.model.WorkItem;
import io.quarkiverse.work.runtime.model.WorkItemPriority;

/**
 * Built-in FilterAction that sets the priority of a WorkItem.
 *
 * <p>
 * Params: {@code priority} (required) — must be a valid {@link WorkItemPriority} name
 * (e.g. {@code CRITICAL}, {@code HIGH}, {@code NORMAL}, {@code LOW}).
 * Skips silently if {@code priority} is null or not a recognised enum value.
 */
@ApplicationScoped
public class SetPriorityAction implements FilterAction {

    @Override
    public String type() {
        return "SET_PRIORITY";
    }

    @Override
    public void apply(final Object workUnit, final Map<String, Object> params) {
        final WorkItem workItem = (WorkItem) workUnit;
        final Object priorityParam = params.get("priority");
        if (priorityParam == null) {
            return;
        }
        try {
            workItem.priority = WorkItemPriority.valueOf(priorityParam.toString().toUpperCase());
            // No put() call — the outer transaction flushes the dirty entity at commit
        } catch (final IllegalArgumentException e) {
            // Invalid priority value in filter params — log a warning and skip the action
            // rather than propagating an exception that would roll back the outer WorkItem mutation
            org.jboss.logging.Logger.getLogger(SetPriorityAction.class)
                    .warnf("SetPriorityAction: invalid priority '%s' — action skipped", priorityParam);
        }
    }
}
