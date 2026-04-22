package io.quarkiverse.work.core.filter;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Immutable definition of a filter rule produced by a CDI bean (@Produces FilterDefinition).
 *
 * @param name unique identifier (e.g. "ai/low-confidence")
 * @param description human-readable explanation
 * @param enabled whether this filter is active; can be toggled at runtime
 * @param events which lifecycle events trigger evaluation
 * @param condition JEXL expression evaluated against the WorkItem; true → apply actions
 * @param conditionContext additional variables exposed in the JEXL evaluation context
 * @param actions ordered list of actions to apply when condition is true
 */
public record FilterDefinition(
        String name,
        String description,
        boolean enabled,
        Set<FilterEvent> events,
        String condition,
        Map<String, Object> conditionContext,
        List<ActionDescriptor> actions) {

    /** Convenience factory — fires on all three event types. */
    public static FilterDefinition onAll(final String name, final String description,
            final boolean enabled, final String condition,
            final Map<String, Object> conditionContext,
            final List<ActionDescriptor> actions) {
        return new FilterDefinition(name, description, enabled,
                Set.of(FilterEvent.values()), condition, conditionContext, actions);
    }

    /** Convenience factory — fires on ADD only. */
    public static FilterDefinition onAdd(final String name, final String description,
            final boolean enabled, final String condition,
            final Map<String, Object> conditionContext,
            final List<ActionDescriptor> actions) {
        return new FilterDefinition(name, description, enabled,
                Set.of(FilterEvent.ADD), condition, conditionContext, actions);
    }
}
