package io.quarkiverse.workitems.filterregistry.registry;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.workitems.filterregistry.spi.FilterDefinition;

/**
 * Registry of DB-persisted filter rules. Full implementation added in Task 7
 * (FilterRule entity + V3001 migration). This stub is wired by CDI now so
 * FilterRegistryEngine can be fully wired.
 */
@ApplicationScoped
public class DynamicFilterRegistry {

    /**
     * Returns all enabled dynamic filter definitions.
     * Stub implementation — always returns an empty list until Task 7.
     *
     * @return enabled filter definitions from the database
     */
    public List<FilterDefinition> allEnabled() {
        return List.of();
    }
}
