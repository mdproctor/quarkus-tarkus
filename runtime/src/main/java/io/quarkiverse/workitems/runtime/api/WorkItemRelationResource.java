package io.quarkiverse.workitems.runtime.api;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import io.quarkiverse.workitems.runtime.model.WorkItem;
import io.quarkiverse.workitems.runtime.model.WorkItemRelation;
import io.quarkiverse.workitems.runtime.model.WorkItemRelationType;
import io.quarkiverse.workitems.runtime.repository.WorkItemStore;

/**
 * REST resource for the WorkItem relation graph.
 *
 * <pre>
 * POST   /workitems/{id}/relations             — add a relation
 * GET    /workitems/{id}/relations             — list outgoing relations
 * GET    /workitems/{id}/relations/incoming    — list incoming relations
 * DELETE /workitems/{id}/relations/{relId}     — remove a relation
 * GET    /workitems/{id}/children              — items with PART_OF pointing here
 * GET    /workitems/{id}/parent                — item this one is PART_OF
 * </pre>
 *
 * <h2>Cycle prevention</h2>
 * <p>
 * For {@link WorkItemRelationType#PART_OF}, the source cannot be its own ancestor.
 * This check traverses the existing PART_OF graph upward from {@code targetId}
 * and rejects the relation if {@code sourceId} appears anywhere in the ancestry.
 * Cycle prevention does not apply to other relation types.
 */
@Path("/workitems/{id}/relations")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class WorkItemRelationResource {

    @Inject
    WorkItemStore workItemStore;

    /**
     * Request body for adding a relation.
     *
     * @param targetId the UUID of the target WorkItem
     * @param relationType the relation type string (use {@link WorkItemRelationType} constants
     *        or any custom string)
     * @param createdBy the actor adding this relation
     */
    public record AddRelationRequest(String targetId, String relationType, String createdBy) {
    }

    /**
     * Add a directed relation from this WorkItem to another.
     *
     * <p>
     * For {@link WorkItemRelationType#PART_OF}: the source becomes a child of the
     * target. Cycles are rejected — a WorkItem cannot be its own ancestor.
     *
     * <p>
     * Any non-blank string is accepted as a relation type. Use
     * {@link WorkItemRelationType} constants for well-known types.
     *
     * @param sourceId the source WorkItem UUID (path parameter)
     * @param request the relation to add
     * @return 201 Created, 400 on validation failure or cycle, 409 if already exists
     */
    @POST
    @Transactional
    public Response addRelation(
            @PathParam("id") final UUID sourceId,
            final AddRelationRequest request) {

        if (request == null || request.targetId() == null || request.targetId().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "targetId is required")).build();
        }
        if (request.relationType() == null || request.relationType().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "relationType is required")).build();
        }

        final UUID targetId = UUID.fromString(request.targetId());

        // Cycle prevention for PART_OF: source cannot be its own ancestor
        if (WorkItemRelationType.PART_OF.equals(request.relationType())) {
            if (wouldCreateCycle(sourceId, targetId)) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error",
                                "Adding this PART_OF relation would create a cycle — " +
                                        "a WorkItem cannot be its own ancestor"))
                        .build();
            }
        }

        // Duplicate detection
        if (WorkItemRelation.findExisting(sourceId, targetId, request.relationType()) != null) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(Map.of("error", "Relation already exists")).build();
        }

        final WorkItemRelation rel = new WorkItemRelation();
        rel.sourceId = sourceId;
        rel.targetId = targetId;
        rel.relationType = request.relationType();
        rel.createdBy = request.createdBy() != null ? request.createdBy() : "unknown";
        rel.persist();

        return Response.status(Response.Status.CREATED).entity(toResponse(rel)).build();
    }

    /**
     * List all outgoing relations from this WorkItem (relations where this item is the source).
     *
     * @param sourceId the source WorkItem UUID
     * @return 200 OK with list of outgoing relations; may be empty
     */
    @GET
    public List<Map<String, Object>> listOutgoing(@PathParam("id") final UUID sourceId) {
        return WorkItemRelation.findBySourceId(sourceId).stream().map(this::toResponse).toList();
    }

    /**
     * List all incoming relations pointing at this WorkItem (relations where this item is the target).
     *
     * @param targetId the target WorkItem UUID
     * @return 200 OK with list of incoming relations; may be empty
     */
    @GET
    @Path("/incoming")
    public List<Map<String, Object>> listIncoming(@PathParam("id") final UUID targetId) {
        return WorkItemRelation.findByTargetId(targetId).stream().map(this::toResponse).toList();
    }

    /**
     * Remove a relation.
     *
     * @param sourceId the source WorkItem UUID (for ownership check)
     * @param relationId the relation UUID
     * @return 204 No Content on success, 404 if not found
     */
    @DELETE
    @Path("/{relationId}")
    @Transactional
    public Response deleteRelation(
            @PathParam("id") final UUID sourceId,
            @PathParam("relationId") final UUID relationId) {

        final WorkItemRelation rel = WorkItemRelation.findById(relationId);
        if (rel == null || !rel.sourceId.equals(sourceId)) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "Relation not found")).build();
        }
        rel.delete();
        return Response.noContent().build();
    }

    // ── Cycle detection ───────────────────────────────────────────────────────

    /**
     * Returns {@code true} if adding {@code source PART_OF target} would create a cycle.
     *
     * <p>
     * A cycle exists if {@code source} appears anywhere in the ancestry of {@code target}
     * (i.e. walking up the PART_OF chain from {@code target} reaches {@code source}).
     * Also catches self-loops ({@code source == target}).
     *
     * <p>
     * Uses BFS over existing PART_OF relations. In practice, PART_OF trees are
     * shallow (rarely more than 3–4 levels), so this is fast.
     */
    private boolean wouldCreateCycle(final UUID source, final UUID target) {
        if (source.equals(target)) {
            return true; // self-loop
        }
        // Walk up from target: if we reach source, there is a cycle
        final Set<UUID> visited = new HashSet<>();
        UUID current = target;
        while (current != null) {
            if (current.equals(source)) {
                return true;
            }
            if (!visited.add(current)) {
                break; // already-existing cycle in the graph (shouldn't happen, but safe)
            }
            final UUID next = current;
            current = WorkItemRelation.findBySourceAndType(next, WorkItemRelationType.PART_OF)
                    .stream().findFirst()
                    .map(r -> r.targetId)
                    .orElse(null);
        }
        return false;
    }

    private Map<String, Object> toResponse(final WorkItemRelation rel) {
        return Map.of(
                "id", rel.id,
                "sourceId", rel.sourceId,
                "targetId", rel.targetId,
                "relationType", rel.relationType,
                "createdBy", rel.createdBy,
                "createdAt", rel.createdAt);
    }
}
