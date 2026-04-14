package io.quarkiverse.tarkus.flow;

import jakarta.inject.Inject;

import io.quarkiverse.flow.Flow;

/**
 * Base class for Quarkus-Flow workflow definitions that include Tarkus WorkItem steps.
 *
 * <p>
 * Extend this instead of {@link Flow} to gain access to the {@link #tarkus(String)}
 * DSL method, which creates WorkItem suspension steps that integrate naturally with
 * {@code function()}, {@code agent()}, and other quarkus-flow task types.
 *
 * <pre>
 * {
 *     &#64;code
 *     &#64;ApplicationScoped
 *     public class DocumentApprovalWorkflow extends TarkusFlow {
 *
 *         @Override
 *         public Workflow descriptor() {
 *             return workflow("document-approval")
 *                     .tasks(
 *                             tarkus("legalReview")
 *                                     .title("Legal review required")
 *                                     .candidateGroups("legal-team")
 *                                     .priority(WorkItemPriority.HIGH)
 *                                     .payloadFrom((DocumentDraft d) -> d.toJson())
 *                                     .buildTask(DocumentDraft.class))
 *                     .build();
 *         }
 *     }
 * }
 * </pre>
 */
public abstract class TarkusFlow extends Flow {

    @Inject
    HumanTaskFlowBridge tarkusBridge;

    /**
     * Creates a Tarkus WorkItem suspension task for use inside {@code .tasks()}.
     *
     * @param name unique task name within the workflow definition
     * @return a builder for configuring the WorkItem parameters
     */
    protected TarkusTaskBuilder tarkus(final String name) {
        return new TarkusTaskBuilder(name, tarkusBridge);
    }
}
