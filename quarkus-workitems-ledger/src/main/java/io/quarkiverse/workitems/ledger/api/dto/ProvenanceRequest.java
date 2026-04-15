package io.quarkiverse.tarkus.ledger.api.dto;

/**
 * Request body for setting the source entity provenance on the creation ledger entry.
 *
 * <p>
 * Called by integrating systems (Quarkus-Flow, CaseHub, Qhorus) immediately after WorkItem
 * creation to record which external entity originated the WorkItem. This endpoint exists only
 * in the ledger module; the core REST API is not modified.
 *
 * @param sourceEntityId identifier of the external entity, e.g. {@code "workflow-instance-abc123"}
 * @param sourceEntityType type of the external entity, e.g. {@code "Flow:WorkflowInstance"}
 * @param sourceEntitySystem system owning the external entity, e.g. {@code "quarkus-flow"}
 */
public record ProvenanceRequest(
        String sourceEntityId,
        String sourceEntityType,
        String sourceEntitySystem) {
}
