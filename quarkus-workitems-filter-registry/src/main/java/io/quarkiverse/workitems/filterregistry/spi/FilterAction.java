package io.quarkiverse.workitems.filterregistry.spi;

import java.util.Map;

import io.quarkiverse.workitems.runtime.model.WorkItem;

/**
 * SPI for actions that a filter rule can apply to a WorkItem when its condition matches.
 *
 * <p>
 * Implementations must be {@code @ApplicationScoped} CDI beans. The engine resolves
 * implementations by matching {@link ActionDescriptor#type()} to {@link #type()}.
 *
 * <p>
 * Built-in implementations: {@code APPLY_LABEL}, {@code OVERRIDE_CANDIDATE_GROUPS},
 * {@code SET_PRIORITY}.
 */
public interface FilterAction {

    /**
     * The action type name used in {@link ActionDescriptor#type()}.
     * Must be unique across all implementations.
     */
    String type();

    /**
     * Apply this action to the given WorkItem.
     *
     * @param workItem the WorkItem being processed (already persisted)
     * @param params action-specific parameters from the {@link ActionDescriptor}
     */
    void apply(WorkItem workItem, Map<String, Object> params);
}
