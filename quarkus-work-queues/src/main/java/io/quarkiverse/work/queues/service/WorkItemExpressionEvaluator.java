package io.quarkiverse.work.queues.service;

import io.quarkiverse.work.runtime.model.WorkItem;

/**
 * SPI for evaluating an expression against a WorkItem and returning a boolean result.
 *
 * <p>
 * Aligned with the CNCF Serverless Workflow SDK's {@code ExpressionFactory} concept —
 * implementations are named after the language they evaluate rather than their role
 * in filter condition processing.
 *
 * <p>
 * Implementations are CDI beans discovered by {@link FilterEvaluatorRegistry}.
 * Three built-ins are provided: JEXL (default), JQ, and Lambda (CDI beans).
 *
 * <p>
 * The expression and its language travel together as an {@link ExpressionDescriptor},
 * preventing accidental mismatches between language and expression string.
 *
 * @see ExpressionDescriptor
 * @see FilterEvaluatorRegistry
 */
public interface WorkItemExpressionEvaluator {

    /**
     * Language identifier — used by {@link FilterEvaluatorRegistry} to select
     * the correct evaluator for a given {@link ExpressionDescriptor}.
     * e.g. {@code "jexl"}, {@code "jq"}, {@code "lambda"}.
     *
     * @return the language identifier; must be unique per evaluator
     */
    String language();

    /**
     * Evaluates the expression in {@code descriptor} against the given WorkItem.
     *
     * <p>
     * Must never throw — malformed or unsupported expressions return {@code false}
     * with a warning log rather than propagating an exception.
     *
     * @param workItem the WorkItem to evaluate against
     * @param descriptor the expression descriptor (language + expression string)
     * @return {@code true} if the WorkItem matches the expression; {@code false} otherwise
     */
    boolean evaluate(WorkItem workItem, ExpressionDescriptor descriptor);
}
