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
    Instance<WorkItemExpressionEvaluator> evaluators;

    private final Map<String, WorkItemExpressionEvaluator> index = new HashMap<>();

    @PostConstruct
    void init() {
        for (var e : evaluators) {
            index.put(e.language().toLowerCase(), e);
        }
    }

    /** Returns the evaluator for the language in the given descriptor, or {@code null} if unknown. */
    public WorkItemExpressionEvaluator find(final ExpressionDescriptor descriptor) {
        return descriptor != null ? index.get(descriptor.language().toLowerCase()) : null;
    }

    /** Returns the evaluator for a language string directly. Convenience for ad-hoc evaluation. */
    public WorkItemExpressionEvaluator find(final String language) {
        return language != null ? index.get(language.toLowerCase()) : null;
    }
}
