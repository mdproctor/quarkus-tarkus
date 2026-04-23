package io.quarkiverse.work.examples.flow;

import java.util.List;
import java.util.UUID;

/**
 * Response returned by {@code POST /examples/flow/run}.
 *
 * @param scenario workflow name
 * @param steps chronological log of each step
 * @param workItemIds UUIDs of the WorkItems created during the human steps
 * @param finalResult the output of the last workflow step
 */
public record FlowScenarioResponse(
        String scenario,
        List<String> steps,
        List<UUID> workItemIds,
        String finalResult) {
}
