package io.casehub.work.runtime.multiinstance;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;

import io.casehub.work.api.InstanceAssignmentStrategy;
import io.casehub.work.api.MultiInstanceContext;

/**
 * Applies multiple {@link InstanceAssignmentStrategy} delegates in order.
 *
 * <p>
 * Each delegate sees the full list of instances and the same context. Later strategies
 * may overwrite fields set by earlier ones — callers should order delegates accordingly.
 *
 * <p>
 * Typical use: run {@code PoolAssignmentStrategy} first (sets candidateGroups/Users),
 * then run {@code ExplicitListAssignmentStrategy} (overrides assigneeId for specific
 * instances), leaving the group candidates intact for any unassigned ones.
 */
@ApplicationScoped
@Named("composite")
public class CompositeInstanceAssignmentStrategy implements InstanceAssignmentStrategy {

    private final List<InstanceAssignmentStrategy> delegates;

    /**
     * CDI no-arg constructor — required for Weld proxy generation.
     * Do not use directly; use the list constructor for programmatic construction.
     */
    protected CompositeInstanceAssignmentStrategy() {
        this.delegates = List.of();
    }

    /**
     * Constructs a composite from an ordered list of delegates.
     *
     * @param delegates strategies to apply in order; must not be null
     */
    public CompositeInstanceAssignmentStrategy(final List<InstanceAssignmentStrategy> delegates) {
        this.delegates = List.copyOf(delegates);
    }

    /**
     * Applies each delegate strategy in order to all instances.
     *
     * @param instances ordered list of child WorkItems, not yet persisted by this call
     * @param context parent WorkItem and resolved MultiInstanceConfig
     */
    @Override
    public void assign(final List<Object> instances, final MultiInstanceContext context) {
        for (final InstanceAssignmentStrategy strategy : delegates) {
            strategy.assign(instances, context);
        }
    }
}
