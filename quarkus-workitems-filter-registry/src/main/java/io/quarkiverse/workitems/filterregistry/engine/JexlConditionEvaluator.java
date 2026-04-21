package io.quarkiverse.workitems.filterregistry.engine;

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
 * all known WorkItem fields by name (preserving types, e.g. enums remain enum constants).
 * Additional variables from {@code conditionContext} are also merged into the context.
 *
 * <p>
 * A map-based representation is used because JEXL 3.4 cannot access public fields or
 * getters via reflection under the Java module system (JPMS) in Java 9+, and because
 * Hibernate ORM bytecode enhancement intercepts field storage in entity subclasses —
 * making {@code Field.get()} via reflection unreliable. Map access
 * (e.g. {@code workItem.category}) is fully supported and always returns the correct
 * current value.
 *
 * <h2>Context variables</h2>
 * <p>
 * {@code workItem} is exposed as a {@code Map<String, Object>} with all WorkItem fields.
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
     * Converts a WorkItem to a {@code Map<String, Object>} by reading all known public
     * fields directly (not via reflection).
     *
     * <p>
     * Direct field access is used instead of reflection because Hibernate ORM bytecode
     * enhancement replaces field storage in the entity class; {@code Field.get()} via
     * reflection bypasses the enhancement and returns stale or null values even when the
     * field has been set. Direct access triggers the enhanced getter and returns the
     * correct value.
     *
     * <p>
     * Enum fields are preserved as enum constants so JEXL can call enum methods
     * (e.g. {@code workItem.priority.name()}).
     *
     * @param workItem the WorkItem to convert
     * @return map of field name → field value (null values are included)
     */
    static Map<String, Object> toMap(final WorkItem workItem) {
        final Map<String, Object> map = new HashMap<>();
        map.put("id", workItem.id);
        map.put("version", workItem.version);
        map.put("title", workItem.title);
        map.put("description", workItem.description);
        map.put("category", workItem.category);
        map.put("formKey", workItem.formKey);
        map.put("status", workItem.status);
        map.put("priority", workItem.priority);
        map.put("assigneeId", workItem.assigneeId);
        map.put("owner", workItem.owner);
        map.put("candidateGroups", workItem.candidateGroups);
        map.put("candidateUsers", workItem.candidateUsers);
        map.put("requiredCapabilities", workItem.requiredCapabilities);
        map.put("createdBy", workItem.createdBy);
        map.put("delegationState", workItem.delegationState);
        map.put("delegationChain", workItem.delegationChain);
        map.put("priorStatus", workItem.priorStatus);
        map.put("payload", workItem.payload);
        map.put("resolution", workItem.resolution);
        map.put("claimDeadline", workItem.claimDeadline);
        map.put("expiresAt", workItem.expiresAt);
        map.put("followUpDate", workItem.followUpDate);
        map.put("createdAt", workItem.createdAt);
        map.put("updatedAt", workItem.updatedAt);
        map.put("assignedAt", workItem.assignedAt);
        map.put("startedAt", workItem.startedAt);
        map.put("completedAt", workItem.completedAt);
        map.put("suspendedAt", workItem.suspendedAt);
        map.put("labels", workItem.labels);
        map.put("confidenceScore", workItem.confidenceScore);
        return map;
    }
}
