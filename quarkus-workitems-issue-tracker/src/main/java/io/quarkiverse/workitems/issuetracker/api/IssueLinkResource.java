package io.quarkiverse.workitems.issuetracker.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import io.quarkiverse.workitems.issuetracker.model.WorkItemIssueLink;
import io.quarkiverse.workitems.issuetracker.service.IssueLinkService;
import io.quarkiverse.workitems.issuetracker.spi.IssueTrackerException;

/**
 * REST API for managing links between WorkItems and external issue tracker issues.
 *
 * <pre>
 * POST   /workitems/{id}/issues              — link an existing issue
 * POST   /workitems/{id}/issues/create       — create a new issue and link it
 * GET    /workitems/{id}/issues              — list all linked issues
 * DELETE /workitems/{id}/issues/{linkId}     — remove a link
 * PUT    /workitems/{id}/issues/sync         — refresh status from remote trackers
 * </pre>
 */
@Path("/workitems/{id}/issues")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class IssueLinkResource {

    @Inject
    IssueLinkService linkService;

    /**
     * Request body for linking an existing issue.
     *
     * @param trackerType the tracker type ({@code "github"}, {@code "jira"}, etc.)
     * @param externalRef the tracker-specific issue reference (e.g. {@code "owner/repo#42"})
     * @param linkedBy the actor creating this link
     */
    public record LinkIssueRequest(String trackerType, String externalRef, String linkedBy) {
    }

    /**
     * Request body for creating a new issue and linking it.
     *
     * @param trackerType the tracker type
     * @param title the issue title
     * @param body the issue body (markdown)
     * @param linkedBy the actor creating this link
     */
    public record CreateIssueRequest(String trackerType, String title, String body, String linkedBy) {
    }

    /**
     * Link an existing issue from an external tracker to a WorkItem.
     *
     * <p>
     * Fetches the issue's current title, URL, and status from the remote tracker
     * and stores them. Idempotent — linking the same issue twice returns the existing link.
     *
     * @param workItemId the WorkItem UUID (path parameter)
     * @param request the link request
     * @return 201 Created with the link, 400 on validation failure, 404 if issue not found,
     *         500 on tracker communication failure
     */
    @POST
    @Transactional
    public Response linkIssue(
            @PathParam("id") final UUID workItemId,
            final LinkIssueRequest request) {

        if (request == null || request.trackerType() == null || request.externalRef() == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "trackerType and externalRef are required"))
                    .build();
        }

        try {
            final WorkItemIssueLink link = linkService.linkExistingIssue(
                    workItemId,
                    request.trackerType(),
                    request.externalRef(),
                    request.linkedBy() != null ? request.linkedBy() : "unknown");
            return Response.status(Response.Status.CREATED).entity(toResponse(link)).build();
        } catch (IssueTrackerException e) {
            if (e.isNotFound()) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", e.getMessage())).build();
            }
            return Response.serverError().entity(Map.of("error", e.getMessage())).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", e.getMessage())).build();
        }
    }

    /**
     * Create a new issue in the remote tracker and link it to a WorkItem in one step.
     *
     * <p>
     * The WorkItem UUID is appended to the issue body as a back-reference.
     *
     * @param workItemId the WorkItem UUID (path parameter)
     * @param request the create request
     * @return 201 Created with the link, 400 on validation or unsupported operation, 500 on failure
     */
    @POST
    @Path("/create")
    @Transactional
    public Response createAndLink(
            @PathParam("id") final UUID workItemId,
            final CreateIssueRequest request) {

        if (request == null || request.trackerType() == null || request.title() == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "trackerType and title are required"))
                    .build();
        }

        try {
            final WorkItemIssueLink link = linkService.createAndLink(
                    workItemId,
                    request.trackerType(),
                    request.title(),
                    request.body() != null ? request.body() : "",
                    request.linkedBy() != null ? request.linkedBy() : "unknown");
            return Response.status(Response.Status.CREATED).entity(toResponse(link)).build();
        } catch (IssueTrackerException e) {
            return Response.serverError().entity(Map.of("error", e.getMessage())).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", e.getMessage())).build();
        }
    }

    /**
     * List all issues linked to a WorkItem.
     *
     * @param workItemId the WorkItem UUID (path parameter)
     * @return 200 OK with list of links (may be empty)
     */
    @GET
    public List<Map<String, Object>> listLinks(@PathParam("id") final UUID workItemId) {
        return linkService.listLinks(workItemId).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Remove a link between a WorkItem and an external issue.
     *
     * <p>
     * Does NOT close the issue in the remote tracker — this only removes the local link record.
     * To close the issue, use the issue tracker's own interface or configure auto-close.
     *
     * @param workItemId the WorkItem UUID (path parameter)
     * @param linkId the link UUID (path parameter)
     * @return 204 No Content on success, 404 if the link is not found
     */
    @DELETE
    @Path("/{linkId}")
    @Transactional
    public Response removeLink(
            @PathParam("id") final UUID workItemId,
            @PathParam("linkId") final UUID linkId) {

        return linkService.removeLink(linkId, workItemId)
                ? Response.noContent().build()
                : Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "Link not found")).build();
    }

    /**
     * Refresh the cached status and title of all linked issues by fetching from the remote trackers.
     *
     * <p>
     * Failed fetches for individual links are skipped — partial sync is preferable
     * to an all-or-nothing failure. Returns the count of successfully synced links.
     *
     * @param workItemId the WorkItem UUID (path parameter)
     * @return 200 OK with {@code {"synced": N}}
     */
    @PUT
    @Path("/sync")
    @Transactional
    public Response syncLinks(@PathParam("id") final UUID workItemId) {
        final int synced = linkService.syncLinks(workItemId);
        return Response.ok(Map.of("synced", synced, "workItemId", workItemId)).build();
    }

    private Map<String, Object> toResponse(final WorkItemIssueLink link) {
        return Map.of(
                "id", link.id,
                "workItemId", link.workItemId,
                "trackerType", link.trackerType,
                "externalRef", link.externalRef,
                "title", link.title != null ? link.title : "",
                "url", link.url != null ? link.url : "",
                "status", link.status,
                "linkedAt", link.linkedAt,
                "linkedBy", link.linkedBy);
    }
}
