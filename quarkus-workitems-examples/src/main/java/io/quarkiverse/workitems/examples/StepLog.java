package io.quarkiverse.tarkus.examples;

import java.util.UUID;

/**
 * A single logged step in a scenario run.
 *
 * @param step 1-based step number
 * @param description human-readable description of what happened
 * @param workItemId the WorkItem affected by this step; {@code null} for non-WorkItem steps
 */
public record StepLog(int step, String description, UUID workItemId) {
}
