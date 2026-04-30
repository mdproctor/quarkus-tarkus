package io.casehub.work.api;

import java.util.Map;
import java.util.UUID;

/**
 * Specification for a single child to spawn.
 *
 * @param templateId the UUID of the WorkItemTemplate to instantiate
 * @param callerRef opaque routing key stored on the spawned WorkItem and echoed
 *        in every lifecycle event — never interpreted by quarkus-work;
 *        CaseHub embeds its planItemId here for completion routing
 * @param overrides fields that override the template defaults for this child only
 *        (e.g. {@code "candidateGroups"} → {@code "fraud-team"})
 */
public record ChildSpec(
        UUID templateId,
        String callerRef,
        Map<String, Object> overrides) {
}
