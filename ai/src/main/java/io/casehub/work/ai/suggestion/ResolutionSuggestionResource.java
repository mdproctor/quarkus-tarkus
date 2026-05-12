package io.casehub.work.ai.suggestion;

import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.runtime.repository.WorkItemStore;

/**
 * REST endpoint for AI-assisted resolution suggestions.
 *
 * <pre>
 * GET /workitems/{id}/resolution-suggestion
 * </pre>
 *
 * <p>
 * Returns a suggested resolution JSON for the WorkItem, derived from similar past
 * completed items used as few-shot examples for a {@code ChatLanguageModel}.
 *
 * <p>
 * Always returns 200. The {@code suggestion} field is {@code null} when no suggestion
 * could be produced (model not configured, no examples found, or model call failed).
 * Check {@code modelAvailable} to distinguish "no model" from "model returned nothing".
 */
@Path("/workitems")
@Produces(MediaType.APPLICATION_JSON)
public class ResolutionSuggestionResource {

    @Inject
    WorkItemStore workItemStore;

    @Inject
    ResolutionSuggestionService suggestionService;

    /**
     * Return an AI-suggested resolution for the given WorkItem.
     *
     * @param id the WorkItem UUID
     * @return 200 with suggestion response, or 404 if WorkItem not found
     */
    @GET
    @Path("/{id}/resolution-suggestion")
    public Response suggest(@PathParam("id") final UUID id) {
        final WorkItem workItem = workItemStore.get(id).orElse(null);
        if (workItem == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("{\"error\":\"WorkItem not found: " + id + "\"}")
                    .build();
        }

        if (!suggestionService.isModelAvailable()) {
            return Response.ok(ResolutionSuggestionResponse.noModel(id)).build();
        }

        final int exampleCount = suggestionService.exampleCount(workItem);
        final String suggestion = suggestionService.suggest(workItem);

        if (suggestion == null) {
            return Response.ok(ResolutionSuggestionResponse.noSuggestion(id, exampleCount)).build();
        }

        return Response.ok(
                new ResolutionSuggestionResponse(id, suggestion, exampleCount, true)).build();
    }
}
