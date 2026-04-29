package io.quarkiverse.work.runtime.api;

import java.util.List;
import java.util.UUID;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import io.quarkiverse.work.api.GroupStatus;
import io.quarkiverse.work.runtime.model.WorkItem;
import io.quarkiverse.work.runtime.model.WorkItemSpawnGroup;

/**
 * REST endpoint for accessing the parallel instances of a multi-instance WorkItem.
 *
 * <p>
 * {@code GET /workitems/{id}/instances} returns the N child instances of a multi-instance
 * parent together with the M-of-N group summary (instanceCount, requiredCount,
 * completedCount, groupStatus). Returns 404 for non-existent or non-multi-instance parents.
 */
@Path("/workitems")
@Produces(MediaType.APPLICATION_JSON)
public class WorkItemInstancesResource {

    /**
     * Response projection for the instances endpoint — parent summary + child list.
     *
     * @param parentId the UUID of the multi-instance parent WorkItem
     * @param groupId the UUID of the spawn group tracking this group's policy
     * @param instanceCount total number of parallel instances spawned
     * @param requiredCount minimum completions needed for the group to succeed
     * @param completedCount instances that have reached COMPLETED status
     * @param rejectedCount instances that have reached a non-COMPLETED terminal status
     * @param groupStatus aggregate status: IN_PROGRESS, COMPLETED, or REJECTED
     * @param instances the child WorkItems in this group
     */
    public record InstancesResponse(
            UUID parentId,
            UUID groupId,
            int instanceCount,
            int requiredCount,
            int completedCount,
            int rejectedCount,
            String groupStatus,
            List<WorkItemResponse> instances) {
    }

    /**
     * Return all child instances of a multi-instance parent with group summary.
     *
     * @param parentId the UUID of the parent WorkItem
     * @return 200 with InstancesResponse, 404 if parent not found or not multi-instance
     */
    @GET
    @Path("/{id}/instances")
    public Response getInstances(@PathParam("id") final UUID parentId) {
        final WorkItem parent = WorkItem.findById(parentId);
        if (parent == null) {
            return Response.status(404).build();
        }

        final WorkItemSpawnGroup group = WorkItemSpawnGroup.findMultiInstanceByParentId(parentId);
        if (group == null) {
            return Response.status(404).build();
        }

        final List<WorkItem> children = WorkItem.list("parentId", parentId);
        final GroupStatus status = group.policyTriggered
                ? (group.completedCount >= group.requiredCount ? GroupStatus.COMPLETED : GroupStatus.REJECTED)
                : GroupStatus.IN_PROGRESS;

        return Response.ok(new InstancesResponse(
                parentId,
                group.id,
                group.instanceCount,
                group.requiredCount,
                group.completedCount,
                group.rejectedCount,
                status.name(),
                children.stream().map(WorkItemMapper::toResponse).toList())).build();
    }
}
