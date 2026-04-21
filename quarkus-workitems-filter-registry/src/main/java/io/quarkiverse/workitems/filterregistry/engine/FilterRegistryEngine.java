package io.quarkiverse.workitems.filterregistry.engine;

import java.util.List;
import java.util.stream.Stream;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import io.quarkiverse.workitems.filterregistry.registry.DynamicFilterRegistry;
import io.quarkiverse.workitems.filterregistry.registry.PermanentFilterRegistry;
import io.quarkiverse.workitems.filterregistry.spi.ActionDescriptor;
import io.quarkiverse.workitems.filterregistry.spi.FilterAction;
import io.quarkiverse.workitems.filterregistry.spi.FilterDefinition;
import io.quarkiverse.workitems.filterregistry.spi.FilterEvent;
import io.quarkiverse.workitems.runtime.event.WorkItemLifecycleEvent;
import io.quarkiverse.workitems.runtime.model.WorkItem;
import io.quarkiverse.workitems.runtime.repository.WorkItemStore;

/**
 * Observes WorkItemLifecycleEvent, evaluates all enabled filter definitions,
 * and applies matching actions.
 *
 * <p>
 * A ThreadLocal guard prevents recursive triggering when actions (e.g. label
 * application) themselves fire lifecycle events.
 */
@ApplicationScoped
public class FilterRegistryEngine {

    private static final ThreadLocal<Boolean> RUNNING = ThreadLocal.withInitial(() -> false);

    private static final List<String> TERMINAL_EVENTS = List.of("completed", "rejected", "cancelled", "expired", "escalated");

    private final WorkItemStore workItemStore;
    private final JexlConditionEvaluator conditionEvaluator;
    private final List<FilterAction> filterActions;
    private final PermanentFilterRegistry permanentRegistry;
    private final DynamicFilterRegistry dynamicRegistry;

    @Inject
    public FilterRegistryEngine(final WorkItemStore workItemStore,
            final JexlConditionEvaluator conditionEvaluator,
            final Instance<FilterAction> filterActions,
            final PermanentFilterRegistry permanentRegistry,
            final DynamicFilterRegistry dynamicRegistry) {
        this.workItemStore = workItemStore;
        this.conditionEvaluator = conditionEvaluator;
        this.filterActions = filterActions.stream().toList();
        this.permanentRegistry = permanentRegistry;
        this.dynamicRegistry = dynamicRegistry;
    }

    /** Constructor for unit tests — bypasses CDI. */
    FilterRegistryEngine(final WorkItemStore workItemStore,
            final JexlConditionEvaluator conditionEvaluator,
            final List<FilterAction> filterActions) {
        this.workItemStore = workItemStore;
        this.conditionEvaluator = conditionEvaluator;
        this.filterActions = filterActions;
        this.permanentRegistry = new PermanentFilterRegistry();
        this.dynamicRegistry = new DynamicFilterRegistry();
    }

    /**
     * CDI observer — fires on every WorkItem lifecycle transition.
     * A ThreadLocal guard prevents re-entrant processing when actions themselves trigger events.
     *
     * @param event the lifecycle event
     */
    void onLifecycleEvent(@Observes final WorkItemLifecycleEvent event) {
        if (Boolean.TRUE.equals(RUNNING.get())) {
            return;
        }
        RUNNING.set(true);
        try {
            final WorkItem workItem = workItemStore.get(event.workItemId()).orElse(null);
            if (workItem == null)
                return;
            final List<FilterDefinition> allDefs = Stream.concat(
                    permanentRegistry.allEnabled().stream(),
                    dynamicRegistry.allEnabled().stream()).toList();
            applyFilters(event, workItem, allDefs);
        } finally {
            RUNNING.remove();
        }
    }

    /**
     * Package-visible for unit testing: evaluate definitions against the event without
     * going through the CDI observer or reentrancy guard.
     * <p>
     * <strong>Note:</strong> bypasses the {@code ThreadLocal} reentrancy guard — for unit
     * tests only. Do not call from production code paths.
     *
     * @param event the lifecycle event
     * @param defs the filter definitions to evaluate
     */
    void processEvent(final WorkItemLifecycleEvent event, final List<FilterDefinition> defs) {
        final WorkItem workItem = workItemStore.get(event.workItemId()).orElse(null);
        if (workItem == null)
            return;
        applyFilters(event, workItem, defs);
    }

    private void applyFilters(final WorkItemLifecycleEvent event,
            final WorkItem workItem, final List<FilterDefinition> defs) {
        final FilterEvent filterEvent = toFilterEvent(event.type());
        for (final FilterDefinition def : defs) {
            if (!def.enabled())
                continue;
            if (!def.events().contains(filterEvent))
                continue;
            if (!conditionEvaluator.evaluate(def.condition(), def.conditionContext(), workItem))
                continue;
            for (final ActionDescriptor action : def.actions()) {
                filterActions.stream()
                        .filter(a -> a.type().equals(action.type()))
                        .findFirst()
                        .ifPresent(a -> a.apply(workItem, action.params()));
            }
        }
    }

    private FilterEvent toFilterEvent(final String eventType) {
        final String name = eventType.substring(eventType.lastIndexOf('.') + 1).toLowerCase();
        if ("created".equals(name))
            return FilterEvent.ADD;
        if (TERMINAL_EVENTS.contains(name))
            return FilterEvent.REMOVE;
        return FilterEvent.UPDATE;
    }
}
