package io.quarkiverse.work.core.filter;

import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;

import org.apache.commons.jexl3.JexlBuilder;
import org.apache.commons.jexl3.JexlEngine;
import org.apache.commons.jexl3.JexlException;
import org.apache.commons.jexl3.MapContext;

/**
 * Evaluates JEXL conditions against a pre-built work unit context map.
 *
 * <p>
 * The work unit is exposed as {@code workItem} in the JEXL context (key kept for
 * backward compatibility with existing filter rule expressions). Additional variables
 * from {@code conditionContext} are merged alongside.
 *
 * <p>
 * The caller is responsible for building the context map. In quarkus-work,
 * {@code WorkItemContextBuilder.toMap(WorkItem)} provides it via
 * {@code WorkItemLifecycleEvent.context()}.
 */
@ApplicationScoped
public class JexlConditionEvaluator {

    private static final JexlEngine JEXL = new JexlBuilder()
            .strict(false).silent(true).create();

    /**
     * Evaluates a JEXL expression.
     *
     * @param condition JEXL expression; blank/null → false
     * @param conditionContext additional variables merged into the JEXL context; may be null
     * @param workUnitContext the work unit's field map, exposed as {@code workItem} in JEXL
     * @return true iff expression evaluates to Boolean.TRUE
     */
    public boolean evaluate(final String condition,
            final Map<String, Object> conditionContext,
            final Map<String, Object> workUnitContext) {
        if (condition == null || condition.isBlank()) {
            return false;
        }
        try {
            final var ctx = new MapContext();
            ctx.set("workItem", workUnitContext);
            if (conditionContext != null) {
                conditionContext.forEach(ctx::set);
            }
            final Object result = JEXL.createExpression(condition).evaluate(ctx);
            return Boolean.TRUE.equals(result);
        } catch (JexlException e) {
            return false;
        }
    }
}
