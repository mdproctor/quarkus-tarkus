package io.quarkiverse.work.flow;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import io.quarkiverse.work.runtime.event.WorkItemLifecycleEvent;

/**
 * Observes WorkItem lifecycle CDI events and completes pending
 * CompletableFutures in {@link PendingWorkItemRegistry} so suspended
 * Quarkus Flow workflows can resume.
 */
@ApplicationScoped
public class WorkItemFlowEventListener {

    @Inject
    PendingWorkItemRegistry registry;

    void onWorkItemEvent(@Observes final WorkItemLifecycleEvent event) {
        switch (event.type()) {
            case "io.quarkiverse.work.workitem.completed" ->
                registry.complete(event.workItemId(), event.detail());
            case "io.quarkiverse.work.workitem.rejected" ->
                registry.fail(event.workItemId(), event.detail() != null ? event.detail() : "rejected");
            case "io.quarkiverse.work.workitem.cancelled" ->
                registry.fail(event.workItemId(), "cancelled");
            default -> {
                /* ignore all other event types */ }
        }
    }
}
