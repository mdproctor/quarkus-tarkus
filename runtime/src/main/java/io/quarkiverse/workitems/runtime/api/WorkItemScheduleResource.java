package io.quarkiverse.workitems.runtime.api;

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

import io.quarkiverse.workitems.runtime.model.WorkItemSchedule;
import io.quarkiverse.workitems.runtime.service.WorkItemScheduleService;

/**
 * REST resource for managing {@link WorkItemSchedule} records.
 *
 * <pre>
 * POST   /workitem-schedules                 — create a schedule
 * GET    /workitem-schedules                 — list all schedules
 * GET    /workitem-schedules/{id}            — get a single schedule
 * DELETE /workitem-schedules/{id}            — delete a schedule
 * PUT    /workitem-schedules/{id}/active     — enable or disable
 * </pre>
 */
@Path("/workitem-schedules")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class WorkItemScheduleResource {

    @Inject
    WorkItemScheduleService scheduleService;

    /**
     * @param name human-readable schedule name (required)
     * @param templateId the template UUID to instantiate on each firing (required)
     * @param cronExpression Quartz cron expression — 6 fields (required)
     * @param createdBy who created this schedule (required)
     */
    public record CreateScheduleRequest(
            String name, String templateId, String cronExpression, String createdBy) {
    }

    /** @param active whether the schedule should fire */
    public record SetActiveRequest(boolean active) {
    }

    /**
     * Create a new recurring schedule.
     *
     * @return 201 Created with the schedule; 400 if required fields missing or cron invalid
     */
    @POST
    @Transactional
    public Response create(final CreateScheduleRequest request) {
        if (request == null || request.name() == null || request.name().isBlank()) {
            return bad("name is required");
        }
        if (request.templateId() == null || request.templateId().isBlank()) {
            return bad("templateId is required");
        }
        if (request.cronExpression() == null || request.cronExpression().isBlank()) {
            return bad("cronExpression is required");
        }
        try {
            final WorkItemSchedule s = scheduleService.create(
                    request.name(),
                    UUID.fromString(request.templateId()),
                    request.cronExpression(),
                    request.createdBy() != null ? request.createdBy() : "unknown");
            return Response.status(Response.Status.CREATED).entity(toResponse(s)).build();
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().toLowerCase().contains("cron")) {
                return bad("Invalid cron expression: " + e.getMessage());
            }
            return bad("Invalid cron expression — use Quartz format, e.g. '0 0 9 * * ?'");
        }
    }

    /** List all schedules ordered by name. */
    @GET
    public List<Map<String, Object>> list() {
        return WorkItemSchedule.listAllByName().stream().map(this::toResponse).toList();
    }

    /** Get a single schedule by ID; 404 if not found. */
    @GET
    @Path("/{id}")
    public Response get(@PathParam("id") final UUID id) {
        return scheduleService.findById(id)
                .map(s -> Response.ok(toResponse(s)).build())
                .orElseGet(() -> notFound());
    }

    /** Delete a schedule; 204 on success, 404 if not found. */
    @DELETE
    @Path("/{id}")
    @Transactional
    public Response delete(@PathParam("id") final UUID id) {
        return WorkItemSchedule.deleteById(id) ? Response.noContent().build() : notFound();
    }

    /**
     * Enable or disable a schedule.
     * Re-enabling recomputes {@code nextFireAt} so missed periods while inactive
     * are not all fired at once.
     */
    @PUT
    @Path("/{id}/active")
    @Transactional
    public Response setActive(@PathParam("id") final UUID id, final SetActiveRequest request) {
        if (request == null)
            return bad("body required: {\"active\": true|false}");
        try {
            return scheduleService.setActive(id, request.active())
                    .map(s -> Response.ok(toResponse(s)).build())
                    .orElseGet(() -> notFound());
        } catch (Exception e) {
            return bad("Could not recompute nextFireAt: " + e.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Map<String, Object> toResponse(final WorkItemSchedule s) {
        final java.util.LinkedHashMap<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("id", s.id);
        m.put("name", s.name);
        m.put("templateId", s.templateId);
        m.put("cronExpression", s.cronExpression);
        m.put("active", s.active);
        m.put("createdBy", s.createdBy);
        m.put("createdAt", s.createdAt);
        m.put("lastFiredAt", s.lastFiredAt);
        m.put("nextFireAt", s.nextFireAt);
        return m;
    }

    private Response bad(final String msg) {
        return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", msg)).build();
    }

    private Response notFound() {
        return Response.status(Response.Status.NOT_FOUND).entity(Map.of("error", "Schedule not found")).build();
    }
}
