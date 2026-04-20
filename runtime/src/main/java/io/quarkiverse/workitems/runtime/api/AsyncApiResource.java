package io.quarkiverse.workitems.runtime.api;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;

/**
 * Serves the AsyncAPI specification for Quarkus WorkItems CDI events.
 *
 * <p>
 * Available at {@code GET /q/asyncapi} — mirrors the OpenAPI convention
 * of {@code GET /q/openapi} for REST API discovery.
 *
 * <p>
 * The spec documents:
 * <ul>
 * <li>{@link io.quarkiverse.workitems.runtime.event.WorkItemLifecycleEvent} —
 * fired on every WorkItem state transition</li>
 * <li>{@code WorkItemQueueEvent} — fired on queue membership changes
 * (requires {@code quarkus-workitems-queues})</li>
 * </ul>
 *
 * <p>
 * The source file lives at {@code docs/asyncapi.yaml} in the repository
 * and is included in the runtime JAR at {@code asyncapi.yaml}.
 */
@Path("/q/asyncapi")
public class AsyncApiResource {

    private static final String SPEC_PATH = "/asyncapi.yaml";

    /**
     * Return the AsyncAPI 3.0 specification as YAML.
     *
     * @return 200 OK with the spec, or 503 if the resource cannot be loaded
     */
    @GET
    @Produces("application/yaml")
    public Response asyncApi() {
        try (InputStream is = AsyncApiResource.class.getResourceAsStream(SPEC_PATH)) {
            if (is == null) {
                return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                        .entity("asyncapi.yaml not found on classpath").build();
            }
            final String yaml = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            return Response.ok(yaml).build();
        } catch (final IOException e) {
            return Response.serverError().entity("Failed to read asyncapi.yaml: " + e.getMessage()).build();
        }
    }
}
