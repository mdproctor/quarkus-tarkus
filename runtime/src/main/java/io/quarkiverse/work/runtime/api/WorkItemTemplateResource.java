package io.quarkiverse.work.runtime.api;

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
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import io.quarkiverse.work.runtime.model.WorkItemTemplate;
import io.quarkiverse.work.runtime.service.WorkItemTemplateService;
import io.quarkiverse.work.runtime.service.WorkItemTemplateValidationService;

/**
 * REST resource for managing and instantiating {@link WorkItemTemplate} records.
 *
 * <pre>
 * POST   /workitem-templates                         — create a template
 * GET    /workitem-templates                         — list all templates
 * GET    /workitem-templates/{id}                    — get a single template
 * DELETE /workitem-templates/{id}                    — delete a template
 * POST   /workitem-templates/{id}/instantiate        — create a WorkItem from the template
 * </pre>
 */
@Path("/workitem-templates")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class WorkItemTemplateResource {

    @Inject
    WorkItemTemplateService templateService;

    /**
     * Request body for creating a new template.
     *
     * @param name display name (required); used as default WorkItem title
     * @param description optional description of what this template is for
     * @param category default WorkItem category
     * @param priority default WorkItem priority
     * @param candidateGroups default comma-separated candidate groups
     * @param candidateUsers default comma-separated candidate users
     * @param requiredCapabilities default comma-separated capability tags
     * @param defaultExpiryHours default completion deadline in hours; null → system default
     * @param defaultClaimHours default claim deadline in hours; null → system default
     * @param defaultExpiryBusinessHours default completion deadline in business hours
     * @param defaultClaimBusinessHours default claim deadline in business hours
     * @param defaultPayload default JSON payload
     * @param labelPaths JSON array of label paths applied at instantiation
     * @param instanceCount number of parallel instances for multi-instance mode; null for standard
     * @param requiredCount minimum instances that must complete; only meaningful when instanceCount is set
     * @param parentRole COORDINATOR (default) or PARTICIPANT; only meaningful when instanceCount is set
     * @param assignmentStrategy CDI bean name of InstanceAssignmentStrategy; null defaults to "pool"
     * @param onThresholdReached CANCEL (default) or LEAVE when threshold met
     * @param allowSameAssignee when true, same person can claim multiple instances in group
     * @param createdBy who created this template (required)
     */
    public record CreateTemplateRequest(
            String name,
            String description,
            String category,
            String priority,
            String candidateGroups,
            String candidateUsers,
            String requiredCapabilities,
            Integer defaultExpiryHours,
            Integer defaultClaimHours,
            Integer defaultExpiryBusinessHours,
            Integer defaultClaimBusinessHours,
            String defaultPayload,
            String labelPaths,
            Integer instanceCount,
            Integer requiredCount,
            String parentRole,
            String assignmentStrategy,
            String onThresholdReached,
            Boolean allowSameAssignee,
            String createdBy) {
    }

    /**
     * Request body for instantiating a template.
     *
     * @param title optional title override; defaults to template name
     * @param assigneeId optional direct assignee; bypasses candidateGroups routing
     * @param createdBy who or what is triggering the instantiation (required)
     */
    public record InstantiateRequest(String title, String assigneeId, String createdBy) {
    }

    /**
     * Create a new WorkItemTemplate.
     *
     * @param request the template definition; {@code name} and {@code createdBy} are required
     * @return 201 Created with the new template, 400 if required fields are missing
     */
    @POST
    @Transactional
    public Response createTemplate(final CreateTemplateRequest request) {
        if (request == null || request.name() == null || request.name().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "name is required")).build();
        }
        if (request.createdBy() == null || request.createdBy().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "createdBy is required")).build();
        }

        final WorkItemTemplate t = new WorkItemTemplate();
        t.name = request.name();
        t.description = request.description();
        t.category = request.category();
        t.priority = request.priority() != null
                ? io.quarkiverse.work.runtime.model.WorkItemPriority.valueOf(request.priority())
                : null;
        t.candidateGroups = request.candidateGroups();
        t.candidateUsers = request.candidateUsers();
        t.requiredCapabilities = request.requiredCapabilities();
        t.defaultExpiryHours = request.defaultExpiryHours();
        t.defaultClaimHours = request.defaultClaimHours();
        t.defaultExpiryBusinessHours = request.defaultExpiryBusinessHours();
        t.defaultClaimBusinessHours = request.defaultClaimBusinessHours();
        t.defaultPayload = request.defaultPayload();
        t.labelPaths = request.labelPaths();
        t.instanceCount = request.instanceCount();
        t.requiredCount = request.requiredCount();
        t.parentRole = request.parentRole();
        t.assignmentStrategy = request.assignmentStrategy();
        t.onThresholdReached = request.onThresholdReached();
        t.allowSameAssignee = request.allowSameAssignee();
        t.createdBy = request.createdBy();
        WorkItemTemplateValidationService.validate(t);
        t.persist();

        return Response.status(Response.Status.CREATED).entity(toResponse(t)).build();
    }

    /**
     * List all WorkItemTemplates, ordered by name.
     *
     * @return 200 OK with list of templates; may be empty
     */
    @GET
    public List<Map<String, Object>> listTemplates() {
        return WorkItemTemplate.listAllByName().stream().map(this::toResponse).toList();
    }

    /**
     * Get a single WorkItemTemplate by ID.
     *
     * @param id the template UUID
     * @return 200 OK with the template, 404 if not found
     */
    @GET
    @Path("/{id}")
    public Response getTemplate(@PathParam("id") final UUID id) {
        return templateService.findById(id)
                .map(t -> Response.ok(toResponse(t)).build())
                .orElseGet(() -> Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "Template not found")).build());
    }

    /**
     * Delete a WorkItemTemplate.
     *
     * <p>
     * Does NOT delete WorkItems previously instantiated from this template.
     *
     * @param id the template UUID
     * @return 204 No Content on success, 404 if not found
     */
    @DELETE
    @Path("/{id}")
    @Transactional
    public Response deleteTemplate(@PathParam("id") final UUID id) {
        return WorkItemTemplate.deleteById(id)
                ? Response.noContent().build()
                : Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "Template not found")).build();
    }

    /**
     * Create a WorkItem from a template.
     *
     * <p>
     * The WorkItem inherits all template defaults. The caller may override the title
     * and assignee; all other fields are fixed by the template.
     *
     * @param id the template UUID
     * @param request optional overrides ({@code title}, {@code assigneeId}) and {@code createdBy}
     * @return 201 Created with the new WorkItem, 400 if createdBy missing, 404 if template not found
     */
    @POST
    @Path("/{id}/instantiate")
    @Transactional
    public Response instantiate(@PathParam("id") final UUID id, final InstantiateRequest request) {
        if (request == null || request.createdBy() == null || request.createdBy().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "createdBy is required")).build();
        }
        return templateService.findById(id)
                .map(t -> {
                    final var wi = templateService.instantiate(
                            t, request.title(), request.assigneeId(), request.createdBy());
                    return Response.status(Response.Status.CREATED)
                            .entity(WorkItemMapper.toResponse(wi)).build();
                })
                .orElseGet(() -> Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "Template not found")).build());
    }

    private Map<String, Object> toResponse(final WorkItemTemplate t) {
        final java.util.LinkedHashMap<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("id", t.id);
        m.put("name", t.name);
        m.put("description", t.description);
        m.put("category", t.category);
        m.put("priority", t.priority != null ? t.priority.name() : null);
        m.put("candidateGroups", t.candidateGroups);
        m.put("candidateUsers", t.candidateUsers);
        m.put("requiredCapabilities", t.requiredCapabilities);
        m.put("defaultExpiryHours", t.defaultExpiryHours);
        m.put("defaultClaimHours", t.defaultClaimHours);
        m.put("defaultExpiryBusinessHours", t.defaultExpiryBusinessHours);
        m.put("defaultClaimBusinessHours", t.defaultClaimBusinessHours);
        m.put("defaultPayload", t.defaultPayload);
        m.put("labelPaths", t.labelPaths);
        m.put("instanceCount", t.instanceCount);
        m.put("requiredCount", t.requiredCount);
        m.put("parentRole", t.parentRole);
        m.put("assignmentStrategy", t.assignmentStrategy);
        m.put("onThresholdReached", t.onThresholdReached);
        m.put("allowSameAssignee", t.allowSameAssignee);
        m.put("createdBy", t.createdBy);
        m.put("createdAt", t.createdAt);
        return m;
    }
}
