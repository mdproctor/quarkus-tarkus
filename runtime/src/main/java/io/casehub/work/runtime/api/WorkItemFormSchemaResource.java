package io.casehub.work.runtime.api;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

import io.casehub.work.runtime.model.WorkItemFormSchema;

/**
 * REST resource for managing {@link WorkItemFormSchema} definitions.
 *
 * <pre>
 * POST   /workitem-form-schemas                        — create a schema definition
 * GET    /workitem-form-schemas                        — list all (or filter by ?category=)
 * GET    /workitem-form-schemas/{id}                   — get a single schema
 * DELETE /workitem-form-schemas/{id}                   — delete a schema
 * </pre>
 *
 * <p>
 * Schema payloads are stored as TEXT — valid JSON but not further parsed by WorkItems.
 * JSON Schema validation of WorkItem payloads against these schemas is handled in
 * issue #108 (Epic #98 phase 2).
 *
 * @see <a href="https://github.com/mdproctor/quarkus-work/issues/107">Issue #107</a>
 * @see <a href="https://github.com/mdproctor/quarkus-work/issues/98">Epic #98</a>
 */
@Path("/workitem-form-schemas")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class WorkItemFormSchemaResource {

    /**
     * Request body for creating a new form schema.
     *
     * @param name display name (required)
     * @param category optional category this schema applies to; null means global/catch-all
     * @param payloadSchema JSON Schema for WorkItem payload (stored as TEXT, optional)
     * @param resolutionSchema JSON Schema for WorkItem resolution (stored as TEXT, optional)
     * @param schemaVersion free-form version string (optional)
     * @param createdBy actor creating this schema definition (required)
     */
    public record CreateFormSchemaRequest(
            String name,
            String category,
            String payloadSchema,
            String resolutionSchema,
            String schemaVersion,
            String createdBy) {
    }

    /**
     * Create a new WorkItemFormSchema.
     *
     * @param request schema definition; {@code name} and {@code createdBy} are required
     * @return 201 Created with the new schema, or 400 if required fields are missing
     */
    @POST
    @Transactional
    public Response createSchema(final CreateFormSchemaRequest request) {
        if (request == null || request.name() == null || request.name().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "name is required")).build();
        }
        if (request.createdBy() == null || request.createdBy().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "createdBy is required")).build();
        }

        final WorkItemFormSchema schema = new WorkItemFormSchema();
        schema.name = request.name();
        schema.category = request.category();
        schema.payloadSchema = request.payloadSchema();
        schema.resolutionSchema = request.resolutionSchema();
        schema.schemaVersion = request.schemaVersion();
        schema.createdBy = request.createdBy();
        schema.persist();

        return Response.status(Response.Status.CREATED).entity(toResponse(schema)).build();
    }

    /**
     * List WorkItemFormSchemas.
     *
     * <p>
     * With no query parameters returns all schemas, ordered by name.
     * With {@code ?category=} returns only schemas for that category.
     *
     * @param category optional category filter
     * @return 200 OK with list; may be empty
     */
    @GET
    public List<Map<String, Object>> listSchemas(@QueryParam("category") final String category) {
        final List<WorkItemFormSchema> schemas = (category != null && !category.isBlank())
                ? WorkItemFormSchema.findByCategory(category)
                : WorkItemFormSchema.listAllByName();
        return schemas.stream().map(this::toResponse).toList();
    }

    /**
     * Get a single WorkItemFormSchema by ID.
     *
     * @param id the schema UUID
     * @return 200 OK with the schema, or 404 if not found
     */
    @GET
    @Path("/{id}")
    public Response getSchema(@PathParam("id") final UUID id) {
        final WorkItemFormSchema schema = WorkItemFormSchema.findById(id);
        if (schema == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "FormSchema not found")).build();
        }
        return Response.ok(toResponse(schema)).build();
    }

    /**
     * Delete a WorkItemFormSchema.
     *
     * <p>
     * Deleting a schema does not affect WorkItems that were created using it —
     * WorkItemFormSchema has no foreign key relationship to WorkItem.
     *
     * @param id the schema UUID
     * @return 204 No Content on success, 404 if not found
     */
    @DELETE
    @Path("/{id}")
    @Transactional
    public Response deleteSchema(@PathParam("id") final UUID id) {
        return WorkItemFormSchema.deleteById(id)
                ? Response.noContent().build()
                : Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "FormSchema not found")).build();
    }

    private Map<String, Object> toResponse(final WorkItemFormSchema s) {
        final LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.id);
        m.put("name", s.name);
        m.put("category", s.category);
        m.put("payloadSchema", s.payloadSchema);
        m.put("resolutionSchema", s.resolutionSchema);
        m.put("schemaVersion", s.schemaVersion);
        m.put("createdBy", s.createdBy);
        m.put("createdAt", s.createdAt);
        return m;
    }
}
