package io.quarkiverse.work.runtime.filter;

import java.util.List;
import java.util.stream.Stream;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import io.quarkiverse.work.api.WorkEventType;
import io.quarkiverse.work.api.WorkLifecycleEvent;

/**
 * Observes WorkLifecycleEvent (any subtype), evaluates all enabled filter definitions,
 * and applies matching actions via the FilterAction SPI.
 *
 * <p>
 * A ThreadLocal guard prevents recursive triggering when actions (e.g. label application)
 * themselves fire lifecycle events.
 *
 * <p>
 * The work unit is accessed via {@code event.source()} — no store lookup needed.
 * The context map for JEXL evaluation comes from {@code event.context()}.
 */
@ApplicationScoped
public class FilterRegistryEngine {

    private static final ThreadLocal<Boolean> RUNNING = ThreadLocal.withInitial(() -> false);

    private final JexlConditionEvaluator conditionEvaluator;
    private final List<FilterAction> filterActions;
    private final PermanentFilterRegistry permanentRegistry;
    private final DynamicFilterRegistry dynamicRegistry;

    @Inject
    public FilterRegistryEngine(final JexlConditionEvaluator conditionEvaluator,
            final Instance<FilterAction> filterActions,
            final PermanentFilterRegistry permanentRegistry,
            final DynamicFilterRegistry dynamicRegistry) {
        this.conditionEvaluator = conditionEvaluator;
        this.filterActions = filterActions.stream().toList();
        this.permanentRegistry = permanentRegistry;
        this.dynamicRegistry = dynamicRegistry;
    }

    /** Package-private constructor for unit tests — bypasses CDI. */
    FilterRegistryEngine(final JexlConditionEvaluator conditionEvaluator,
            final List<FilterAction> filterActions) {
        this.conditionEvaluator = conditionEvaluator;
        this.filterActions = filterActions;
        this.permanentRegistry = new PermanentFilterRegistry();
        this.dynamicRegistry = new DynamicFilterRegistry();
    }

    /**
     * CDI observer — fires on every work unit lifecycle transition.
     * A ThreadLocal guard prevents re-entrant processing when actions themselves trigger events.
     *
     * @param event the lifecycle event
     */
    void onLifecycleEvent(@Observes final WorkLifecycleEvent event) {
        if (Boolean.TRUE.equals(RUNNING.get())) {
            return;
        }
        RUNNING.set(true);
        try {
            final List<FilterDefinition> allDefs = Stream.concat(
                    permanentRegistry.allEnabled().stream(),
                    dynamicRegistry.allEnabled().stream()).toList();
            applyFilters(event, allDefs);
        } finally {
            RUNNING.remove();
        }
    }

    /**
     * Package-visible for unit testing: evaluate definitions against the event
     * without going through the CDI observer or reentrancy guard.
     *
     * @param event the lifecycle event
     * @param defs the filter definitions to evaluate
     */
    void processEvent(final WorkLifecycleEvent event, final List<FilterDefinition> defs) {
        applyFilters(event, defs);
    }

    private void applyFilters(final WorkLifecycleEvent event, final List<FilterDefinition> defs) {
        final FilterEvent filterEvent = toFilterEvent(event.eventType());
        final Object workUnit = event.source();
        for (final FilterDefinition def : defs) {
            if (!def.enabled())
                continue;
            if (!def.events().contains(filterEvent))
                continue;
            if (!conditionEvaluator.evaluate(def.condition(), def.conditionContext(), event.context()))
                continue;
            for (final ActionDescriptor action : def.actions()) {
                filterActions.stream()
                        .filter(a -> a.type().equals(action.type()))
                        .findFirst()
                        .ifPresent(a -> a.apply(workUnit, action.params()));
            }
        }
    }

    private FilterEvent toFilterEvent(final WorkEventType eventType) {
        return switch (eventType) {
            case CREATED -> FilterEvent.ADD;
            case COMPLETED, REJECTED, CANCELLED, EXPIRED, ESCALATED -> FilterEvent.REMOVE;
            default -> FilterEvent.UPDATE;
        };
    }
}
