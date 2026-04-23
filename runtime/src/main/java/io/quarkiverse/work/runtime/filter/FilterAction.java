package io.quarkiverse.work.runtime.filter;

import java.util.Map;

/**
 * SPI for actions that a filter rule can apply to a work unit when its condition matches.
 *
 * <p>
 * Implementations must be {@code @ApplicationScoped} CDI beans. The engine resolves
 * them by matching {@link ActionDescriptor#type()} to {@link #type()}.
 *
 * <p>
 * The {@code workUnit} parameter is the domain-specific work object (e.g. {@code WorkItem}
 * in quarkus-work). Implementations cast to their expected concrete type.
 *
 * <p>
 * Built-in implementations (in quarkus-work): {@code APPLY_LABEL},
 * {@code OVERRIDE_CANDIDATE_GROUPS}, {@code SET_PRIORITY}.
 */
public interface FilterAction {

    /** The action type name used in {@link ActionDescriptor#type()}. Must be unique. */
    String type();

    /**
     * Apply this action to the given work unit.
     *
     * @param workUnit the domain work object (cast to concrete type expected by this action)
     * @param params action-specific parameters from the {@link ActionDescriptor}
     */
    void apply(Object workUnit, Map<String, Object> params);
}
