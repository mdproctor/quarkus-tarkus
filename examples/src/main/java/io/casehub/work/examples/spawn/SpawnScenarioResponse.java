package io.casehub.work.examples.spawn;

import java.util.List;

import io.casehub.work.examples.StepLog;

/**
 * Response returned by the spawn scenario endpoint.
 *
 * @param scenario identifier of the scenario that was run
 * @param steps chronological log of each step taken
 * @param parentWorkItemId UUID string of the parent loan-application WorkItem
 * @param childWorkItemIds UUID strings of the three spawned child WorkItems (credit, fraud, compliance)
 */
public record SpawnScenarioResponse(
        String scenario,
        List<StepLog> steps,
        String parentWorkItemId,
        List<String> childWorkItemIds) {
}
