package io.quarkiverse.work.runtime.filter;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

/**
 * Collects CDI-produced {@link FilterDefinition} beans (permanent filters).
 * Enable/disable state is held in-memory — toggling survives the current JVM
 * but resets on restart (permanent filters return to their declared enabled state).
 */
@ApplicationScoped
public class PermanentFilterRegistry {

    private final List<FilterDefinition> definitions;
    private final ConcurrentMap<String, Boolean> enabledOverrides = new ConcurrentHashMap<>();

    @Inject
    public PermanentFilterRegistry(final Instance<FilterDefinition> producers) {
        this.definitions = producers.stream().toList();
    }

    /** No-arg constructor for unit tests (no CDI producers). */
    public PermanentFilterRegistry() {
        this.definitions = List.of();
    }

    /** Returns all filter definitions, with any in-memory enable/disable overrides applied. */
    public List<FilterDefinition> all() {
        return definitions.stream()
                .map(def -> enabledOverrides.containsKey(def.name())
                        ? new FilterDefinition(def.name(), def.description(),
                                enabledOverrides.get(def.name()), def.events(),
                                def.condition(), def.conditionContext(), def.actions())
                        : def)
                .toList();
    }

    /** Returns only enabled filter definitions. */
    public List<FilterDefinition> allEnabled() {
        return all().stream().filter(FilterDefinition::enabled).toList();
    }

    /**
     * Overrides the enabled state of a named filter in memory.
     * Resets to the declared state on restart.
     *
     * @param name the filter name
     * @param enabled the new enabled state
     */
    public void setEnabled(final String name, final boolean enabled) {
        enabledOverrides.put(name, enabled);
    }

    /**
     * Finds a filter definition by name.
     *
     * @param name the filter name
     * @return the definition, or empty if not found
     */
    public Optional<FilterDefinition> findByName(final String name) {
        return all().stream().filter(d -> d.name().equals(name)).findFirst();
    }
}
