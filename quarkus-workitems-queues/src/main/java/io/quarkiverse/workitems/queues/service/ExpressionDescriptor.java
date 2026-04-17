package io.quarkiverse.workitems.queues.service;

/**
 * Bundles an expression language identifier and expression string together.
 *
 * <p>
 * Modelled after the CNCF Serverless Workflow SDK's {@code ExpressionDescriptor} concept —
 * language and expression travel together rather than as separate parameters, preventing
 * mismatched language/expression pairs.
 *
 * <p>
 * Supported languages: {@code "jexl"}, {@code "jq"}, {@code "lambda"}.
 *
 * <p>
 * Examples:
 *
 * <pre>
 * ExpressionDescriptor.of("jexl", "priority == 'HIGH' && assigneeId == null")
 * ExpressionDescriptor.of("jq",   ".priority == \"HIGH\"")
 * </pre>
 */
public record ExpressionDescriptor(
        /**
         * Language identifier — matches the value returned by
         * {@link WorkItemExpressionEvaluator#language()}.
         * e.g. {@code "jexl"}, {@code "jq"}, {@code "lambda"}.
         */
        String language,

        /**
         * The expression string to evaluate.
         * Interpreted according to the semantics of {@code language}.
         */
        String expression) {

    /**
     * Convenience factory method.
     *
     * @param language the expression language identifier
     * @param expression the expression string
     * @return a new descriptor
     */
    public static ExpressionDescriptor of(final String language, final String expression) {
        return new ExpressionDescriptor(language, expression);
    }
}
