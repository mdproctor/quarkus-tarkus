package io.quarkiverse.workitems.queues.service;

import java.util.HashMap;
import java.util.Map;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

@ApplicationScoped
public class FilterEvaluatorRegistry {

    @Inject
    Instance<FilterConditionEvaluator> evaluators;

    private final Map<String, FilterConditionEvaluator> index = new HashMap<>();

    @PostConstruct
    void init() {
        for (var e : evaluators) {
            index.put(e.language().toLowerCase(), e);
        }
    }

    public FilterConditionEvaluator find(final String language) {
        return language != null ? index.get(language.toLowerCase()) : null;
    }
}
