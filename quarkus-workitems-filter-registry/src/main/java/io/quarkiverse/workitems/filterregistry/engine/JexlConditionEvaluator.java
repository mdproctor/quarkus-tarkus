package io.quarkiverse.workitems.filterregistry.engine;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;

import org.apache.commons.jexl3.JexlBuilder;
import org.apache.commons.jexl3.JexlEngine;
import org.apache.commons.jexl3.JexlException;
import org.apache.commons.jexl3.MapContext;

import io.quarkiverse.workitems.runtime.model.WorkItem;

/**
 * Evaluates JEXL conditions against a WorkItem.
 *
 * <p>
 * The JEXL context exposes {@code workItem} as a {@code Map<String, Object>} containing
 * all public fields of the WorkItem (preserving types, e.g. enums remain enum constants).
 * Additional variables from {@code conditionContext} are also merged into the context.
 *
 * <p>
 * Map-based representation is used because JEXL 3.4 cannot access public fields or
 * getters via reflection under the Java module system (JPMS) in Java 9+. Map access
 * (e.g. {@code workItem.category}) is fully supported and avoids reflection restrictions.
 *
 * <h2>Context variables</h2>
 * <p>
 * {@code workItem} is exposed as a {@code Map<String, Object>} with all public fields.
 * Enum fields (e.g. {@code priority}, {@code status}) are exposed as the enum constant,
 * not as a String — use {@code workItem.priority.name() == 'HIGH'} not
 * {@code workItem.priority == 'HIGH'}.
 * <p>
 * {@code labels} is exposed as the raw {@code List<WorkItemLabel>} — access paths via
 * {@code workItem.labels.![path]} (JEXL projection). Note: the queues module's evaluator
 * exposes labels as a flat {@code List<String>} of paths; this evaluator does not.
 */
@ApplicationScoped
public class JexlConditionEvaluator {

    private static final JexlEngine JEXL = new JexlBuilder()
            .strict(false).silent(true).create();

    /**
     * Evaluates a JEXL expression against the given WorkItem.
     *
     * @param condition JEXL expression; blank → false
     * @param conditionContext additional variables merged into the JEXL context
     * @param workItem the WorkItem being evaluated
     * @return true if the expression evaluates to Boolean.TRUE, false otherwise
     */
    public boolean evaluate(final String condition,
            final Map<String, Object> conditionContext, final WorkItem workItem) {
        if (condition == null || condition.isBlank()) {
            return false;
        }
        try {
            final var ctx = new MapContext();
            ctx.set("workItem", toMap(workItem));
            if (conditionContext != null) {
                conditionContext.forEach(ctx::set);
            }
            final Object result = JEXL.createExpression(condition).evaluate(ctx);
            return Boolean.TRUE.equals(result);
        } catch (JexlException e) {
            return false;
        }
    }

    /**
     * Converts a WorkItem to a {@code Map<String, Object>} by reading all public
     * non-static fields via reflection.
     *
     * <p>
     * Enum fields are preserved as enum constants so JEXL can call enum methods
     * (e.g. {@code workItem.priority.name()}).
     *
     * @param workItem the WorkItem to convert
     * @return map of field name → field value
     */
    private static Map<String, Object> toMap(final WorkItem workItem) {
        final Map<String, Object> map = new HashMap<>();
        for (final Field field : workItem.getClass().getFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            try {
                map.put(field.getName(), field.get(workItem));
            } catch (IllegalAccessException e) {
                // skip inaccessible fields
            }
        }
        return map;
    }
}
