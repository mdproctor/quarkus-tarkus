package io.casehub.work.flow;

import static io.serverlessworkflow.fluent.func.FuncWorkflowBuilder.workflow;

import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;

import io.casehub.work.runtime.model.WorkItemPriority;
import io.serverlessworkflow.api.types.Workflow;

/**
 * Test workflow that exercises the WorkItemsFlow DSL.
 * Used by HumanTaskIntegrationTest to verify WorkItem creation via workItem() DSL.
 */
@ApplicationScoped
public class TestWorkItemsWorkflow extends WorkItemsFlow {

    @Override
    public Workflow descriptor() {
        return workflow("test-work-flow")
                .tasks(
                        workItem("legalReview")
                                .title("Legal review required")
                                .candidateGroups("legal-team")
                                .priority(WorkItemPriority.HIGH)
                                .buildTask(Map.class))
                .build();
    }
}
