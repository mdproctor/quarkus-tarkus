package io.quarkiverse.workitems.runtime.api;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import io.quarkiverse.workitems.runtime.model.AuditEntry;
import io.quarkiverse.workitems.runtime.model.WorkItem;
import io.quarkiverse.workitems.runtime.model.WorkItemPriority;
import io.quarkiverse.workitems.runtime.model.WorkItemStatus;
import io.quarkiverse.workitems.runtime.repository.AuditEntryStore;
import io.quarkiverse.workitems.runtime.repository.WorkItemQuery;
import io.quarkiverse.workitems.runtime.repository.WorkItemStore;
import io.quarkiverse.workitems.runtime.service.LabelNotFoundException;
import io.quarkiverse.workitems.runtime.service.WorkItemNotFoundException;
import io.quarkiverse.workitems.runtime.service.WorkItemService;

@Path("/workitems")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class WorkItemResource {

    @Inject
    WorkItemService workItemService;

    @Inject
    AuditEntryStore auditStore;

    @Inject
    WorkItemStore workItemStore;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response create(final CreateWorkItemRequest request) {
        try {
            final WorkItem created = workItemService.create(WorkItemMapper.toServiceRequest(request));
            final URI location = URI.create("/workitems/" + created.id);
            return Response.created(location).entity(WorkItemMapper.toResponse(created)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }

    public record AddLabelRequest(String path, String appliedBy) {
    }

    @GET
    public List<WorkItemResponse> listAll(@QueryParam("label") final String label) {
        if (label != null && !label.isBlank()) {
            return workItemStore.scan(WorkItemQuery.byLabelPattern(label)).stream()
                    .map(WorkItemMapper::toResponse).toList();
        }
        return workItemStore.scan(WorkItemQuery.all()).stream().map(WorkItemMapper::toResponse).toList();
    }

    @GET
    @Path("/inbox")
    public List<WorkItemResponse> inbox(
            @QueryParam("assignee") final String assignee,
            @QueryParam("candidateGroup") final List<String> candidateGroups,
            @QueryParam("candidateUser") final String candidateUser,
            @QueryParam("status") final WorkItemStatus status,
            @QueryParam("priority") final WorkItemPriority priority,
            @QueryParam("category") final String category,
            @QueryParam("followUp") final Boolean followUp) {
        final Instant followUpBefore = Boolean.TRUE.equals(followUp) ? Instant.now() : null;

        final WorkItemQuery.Builder qb = WorkItemQuery.inbox(assignee, candidateGroups, candidateUser).toBuilder();
        if (status != null) {
            qb.status(status);
        }
        if (priority != null) {
            qb.priority(priority);
        }
        if (category != null) {
            qb.category(category);
        }
        if (followUpBefore != null) {
            qb.followUpBefore(followUpBefore);
        }
        return workItemStore.scan(qb.build()).stream().map(WorkItemMapper::toResponse).toList();
    }

    @GET
    @Path("/{id}")
    public WorkItemWithAuditResponse getById(@PathParam("id") final UUID id) {
        final WorkItem wi = workItemStore.get(id)
                .orElseThrow(() -> new WorkItemNotFoundException(id));
        final List<AuditEntry> trail = auditStore.findByWorkItemId(id);
        return WorkItemMapper.toWithAudit(wi, trail);
    }

    @PUT
    @Path("/{id}/claim")
    public WorkItemResponse claim(@PathParam("id") final UUID id,
            @QueryParam("claimant") final String claimant) {
        return WorkItemMapper.toResponse(workItemService.claim(id, claimant));
    }

    @PUT
    @Path("/{id}/start")
    public WorkItemResponse start(@PathParam("id") final UUID id,
            @QueryParam("actor") final String actor) {
        return WorkItemMapper.toResponse(workItemService.start(id, actor));
    }

    @PUT
    @Path("/{id}/complete")
    @Consumes(MediaType.APPLICATION_JSON)
    public WorkItemResponse complete(@PathParam("id") final UUID id,
            @QueryParam("actor") final String actor,
            final CompleteRequest body) {
        return WorkItemMapper.toResponse(workItemService.complete(id, actor, body != null ? body.resolution() : null));
    }

    @PUT
    @Path("/{id}/reject")
    @Consumes(MediaType.APPLICATION_JSON)
    public WorkItemResponse reject(@PathParam("id") final UUID id,
            @QueryParam("actor") final String actor,
            final RejectRequest body) {
        return WorkItemMapper.toResponse(workItemService.reject(id, actor, body != null ? body.reason() : null));
    }

    @PUT
    @Path("/{id}/delegate")
    @Consumes(MediaType.APPLICATION_JSON)
    public WorkItemResponse delegate(@PathParam("id") final UUID id,
            @QueryParam("actor") final String actor,
            final DelegateRequest body) {
        return WorkItemMapper.toResponse(workItemService.delegate(id, actor, body.to()));
    }

    @PUT
    @Path("/{id}/release")
    public WorkItemResponse release(@PathParam("id") final UUID id,
            @QueryParam("actor") final String actor) {
        return WorkItemMapper.toResponse(workItemService.release(id, actor));
    }

    @PUT
    @Path("/{id}/suspend")
    @Consumes(MediaType.APPLICATION_JSON)
    public WorkItemResponse suspend(@PathParam("id") final UUID id,
            @QueryParam("actor") final String actor,
            final SuspendRequest body) {
        return WorkItemMapper.toResponse(workItemService.suspend(id, actor, body != null ? body.reason() : null));
    }

    @PUT
    @Path("/{id}/resume")
    public WorkItemResponse resume(@PathParam("id") final UUID id,
            @QueryParam("actor") final String actor) {
        return WorkItemMapper.toResponse(workItemService.resume(id, actor));
    }

    @PUT
    @Path("/{id}/cancel")
    @Consumes(MediaType.APPLICATION_JSON)
    public WorkItemResponse cancel(@PathParam("id") final UUID id,
            @QueryParam("actor") final String actor,
            final CancelRequest body) {
        return WorkItemMapper.toResponse(workItemService.cancel(id, actor, body != null ? body.reason() : null));
    }

    @POST
    @Path("/{id}/labels")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response addLabel(@PathParam("id") final UUID id, final AddLabelRequest request) {
        if (request == null || request.path() == null || request.path().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "path is required")).build();
        }
        try {
            final WorkItem updated = workItemService.addLabel(id, request.path(),
                    request.appliedBy() != null ? request.appliedBy() : "unknown");
            return Response.ok(WorkItemMapper.toResponse(updated)).build();
        } catch (WorkItemNotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", e.getMessage())).build();
        }
    }

    @DELETE
    @Path("/{id}/labels")
    public Response removeLabel(@PathParam("id") final UUID id,
            @QueryParam("path") final String path) {
        if (path == null || path.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "path is required")).build();
        }
        try {
            final WorkItem updated = workItemService.removeLabel(id, path);
            return Response.ok(WorkItemMapper.toResponse(updated)).build();
        } catch (WorkItemNotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", e.getMessage())).build();
        } catch (LabelNotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", e.getMessage())).build();
        }
    }
}
