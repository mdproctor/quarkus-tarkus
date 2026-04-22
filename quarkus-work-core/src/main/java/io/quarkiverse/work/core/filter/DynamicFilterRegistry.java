package io.quarkiverse.work.core.filter;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Registry of DB-persisted filter rules (dynamic — CRUD via REST at /filter-rules).
 * Rules survive restarts (stored in the filter_rule table, V3001 migration).
 */
@ApplicationScoped
public class DynamicFilterRegistry {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Returns all enabled dynamic filter definitions from the database.
     *
     * @return enabled filter definitions
     */
    public List<FilterDefinition> allEnabled() {
        return FilterRule.allEnabled().stream()
                .map(this::toDefinition)
                .filter(Objects::nonNull)
                .toList();
    }

    private FilterDefinition toDefinition(final FilterRule rule) {
        try {
            final Set<FilterEvent> events = new HashSet<>();
            for (final String e : rule.events.split(",")) {
                events.add(FilterEvent.valueOf(e.trim().toUpperCase()));
            }
            @SuppressWarnings("unchecked")
            final List<Map<String, Object>> rawActions = MAPPER.readValue(rule.actionsJson, List.class);
            final List<ActionDescriptor> actions = rawActions.stream()
                    .map(m -> new ActionDescriptor(
                            (String) m.get("type"),
                            (Map<String, Object>) m.getOrDefault("params", Map.of())))
                    .toList();
            return new FilterDefinition(rule.name, rule.description, rule.enabled,
                    events, rule.condition, Map.of(), actions);
        } catch (Exception e) {
            return null; // skip malformed rules
        }
    }
}
