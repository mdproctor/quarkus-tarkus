package io.casehub.work.examples.queues.lifecycle;

import java.util.List;

import io.casehub.work.queues.event.QueueEventType;

/**
 * Response returned by the {@link QueueLifecycleScenario} endpoint.
 *
 * <p>
 * Each {@link Step} records one operation performed on the WorkItem, an explanation of
 * why a particular queue event type was expected, and the actual events that fired.
 *
 * @param scenarioId unique identifier for this scenario (always {@code "queue-lifecycle-demo"})
 * @param steps ordered list of steps, each showing what happened and which events fired
 */
public record QueueLifecycleResponse(String scenarioId, List<Step> steps) {

    /**
     * One operation in the lifecycle scenario.
     *
     * @param stepNumber 1-based position in the scenario sequence
     * @param operation human-readable description of the action performed
     * @param explanation why this action produces the expected event type
     * @param events the {@link QueueEventType} values fired during this step
     */
    public record Step(int stepNumber, String operation, String explanation, List<QueueEventType> events) {
    }
}
