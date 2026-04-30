package io.casehub.work.runtime.api;

import java.net.URI;
import java.util.List;
import java.util.Map;
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
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import io.casehub.work.api.ChildSpec;
import io.casehub.work.api.SpawnRequest;
import io.casehub.work.api.SpawnResult;
import io.casehub.work.runtime.model.WorkItemSpawnGroup;
import io.casehub.work.runtime.service.WorkItemNotFoundException;
import io.casehub.work.runtime.service.WorkItemSpawnService;

/**
 * REST endpoints for spawning and managing child WorkItems from a parent.
 *
 * <ul>
 * <li>{@code POST /workitems/{id}/spawn} — spawn a group of children from templates</li>
 * <li>{@code GET /workitems/{id}/spawn-groups} — list spawn groups for a parent</li>
 * <li>{@code DELETE /workitems/{id}/spawn-groups/{groupId}} — cancel a spawn group</li>
 * </ul>
 */
@Path("/workitems")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class WorkItemSpawnResource {

    @Inject
    WorkItemSpawnService spawnService;

    /** Request body for a single child spec within a spawn call. */
    public record SpawnChildRequest(String templateId, String callerRef, Map<String, Object> overrides) {
    }

    /** Top-level request body for {@code POST /workitems/{id}/spawn}. */
    public record SpawnBodyRequest(String idempotencyKey, List<SpawnChildRequest> children) {
    }

    /**
     * Spawn a group of child WorkItems from the specified parent.
     *
     * <p>
     * Idempotent: a second call with the same {@code idempotencyKey} returns the existing
     * group (HTTP 200) without creating duplicate children.
     *
     * @param parentId the parent WorkItem UUID
     * @param body spawn request with idempotencyKey and child specs
     * @return 201 Created with group ID and child list (new); 200 OK (idempotent replay);
     *         400 if validation fails; 404 if parent not found; 409 if parent is terminal
     */
    @POST
    @Path("/{id}/spawn")
    @Transactional
    public Response spawn(@PathParam("id") final UUID parentId, final SpawnBodyRequest body) {
        if (body == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "request body is required")).build();
        }
        if (body.children() == null || body.children().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "children must not be empty")).build();
        }
        if (body.idempotencyKey() == null || body.idempotencyKey().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "idempotencyKey is required")).build();
        }
        for (final SpawnChildRequest child : body.children()) {
            if (child.templateId() == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "templateId is required")).build();
            }
        }

        final List<ChildSpec> specs = body.children().stream()
                .map(c -> new ChildSpec(UUID.fromString(c.templateId()), c.callerRef(), c.overrides()))
                .toList();
        final SpawnRequest request = new SpawnRequest(parentId, body.idempotencyKey(), specs);

        try {
            final SpawnResult result = spawnService.spawn(request);
            final Map<String, Object> responseBody = Map.of(
                    "groupId", result.groupId().toString(),
                    "children", result.children().stream()
                            .map(c -> Map.of(
                                    "workItemId", c.workItemId().toString(),
                                    "callerRef", c.callerRef() != null ? c.callerRef() : ""))
                            .toList());
            if (result.created()) {
                return Response.created(URI.create("/spawn-groups/" + result.groupId()))
                        .entity(responseBody).build();
            } else {
                return Response.ok(responseBody).build();
            }
        } catch (WorkItemNotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", e.getMessage())).build();
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(Map.of("error", e.getMessage())).build();
        } catch (IllegalArgumentException e) {
            return Response.status(422)
                    .entity(Map.of("error", e.getMessage())).build();
        }
    }

    /**
     * List all spawn groups for a parent WorkItem, newest first.
     *
     * @param parentId the parent WorkItem UUID
     * @return list of spawn group summaries
     */
    @GET
    @Path("/{id}/spawn-groups")
    public List<Map<String, Object>> listSpawnGroups(@PathParam("id") final UUID parentId) {
        return WorkItemSpawnGroup.findByParentId(parentId).stream()
                .map(g -> Map.<String, Object> of(
                        "id", g.id.toString(),
                        "parentId", g.parentId.toString(),
                        "idempotencyKey", g.idempotencyKey,
                        "createdAt", g.createdAt.toString()))
                .toList();
    }

    /**
     * Cancel a spawn group, optionally cascading cancellation to its PENDING children.
     *
     * @param parentId the parent WorkItem UUID (for ownership check)
     * @param groupId the spawn group UUID
     * @param cancelChildren if true, cancel all PENDING children in this group
     * @return 204 No Content on success; 404 if group not found or does not belong to parent
     */
    @DELETE
    @Path("/{id}/spawn-groups/{groupId}")
    @Transactional
    public Response cancelGroup(
            @PathParam("id") final UUID parentId,
            @PathParam("groupId") final UUID groupId,
            @QueryParam("cancelChildren") final boolean cancelChildren) {
        final WorkItemSpawnGroup group = WorkItemSpawnGroup.findById(groupId);
        if (group == null || !group.parentId.equals(parentId)) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "Spawn group not found")).build();
        }
        try {
            spawnService.cancelGroup(groupId, cancelChildren);
            return Response.noContent().build();
        } catch (WorkItemNotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", e.getMessage())).build();
        }
    }
}
