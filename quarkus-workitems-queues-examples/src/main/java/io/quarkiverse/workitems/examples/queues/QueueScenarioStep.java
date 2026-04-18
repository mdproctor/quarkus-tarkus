package io.quarkiverse.workitems.examples.queues;

import java.util.List;
import java.util.UUID;

/**
 * One step in a runnable queue scenario.
 *
 * @param step 1-based position in the scenario
 * @param description what happened in this step
 * @param workItemId the WorkItem affected (null for queue-snapshot steps)
 * @param inferredLabels INFERRED labels applied by the filter engine
 * @param manualLabels MANUAL labels present on the WorkItem
 * @param queueEvents queue lifecycle events fired during this step, formatted as
 *        {@code "ADDED to Finance Approval Queue"} — one entry per event
 */
public record QueueScenarioStep(
        int step,
        String description,
        UUID workItemId,
        List<String> inferredLabels,
        List<String> manualLabels,
        List<String> queueEvents) {
}
