package io.quarkiverse.workitems.runtime.api;

import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import io.quarkiverse.workitems.runtime.model.LabelDefinition;
import io.quarkiverse.workitems.runtime.model.VocabularyScope;
import io.quarkiverse.workitems.runtime.service.LabelVocabularyService;

@Path("/vocabulary")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class VocabularyResource {

    @Inject
    LabelVocabularyService vocabularyService;

    public record AddDefinitionRequest(String path, String description, String addedBy) {
    }

    /**
     * List all label definitions accessible to the caller.
     * Returns all definitions — scope enforcement deferred to auth context.
     */
    @GET
    public List<Map<String, Object>> listAll() {
        return vocabularyService.listAccessible(VocabularyScope.PERSONAL).stream()
                .map(d -> Map.<String, Object> of(
                        "id", d.id,
                        "path", d.path,
                        "vocabularyId", d.vocabularyId,
                        "description", d.description != null ? d.description : "",
                        "createdBy", d.createdBy,
                        "createdAt", d.createdAt))
                .toList();
    }

    /**
     * Add a label definition to the vocabulary at the given scope.
     * Currently all definitions go into the GLOBAL vocabulary.
     */
    @POST
    @Path("/{scope}")
    @Transactional
    public Response addDefinition(@PathParam("scope") final String scopeStr,
            final AddDefinitionRequest request) {
        if (request == null || request.path() == null || request.path().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "path is required"))
                    .build();
        }

        final VocabularyScope scope;
        try {
            scope = VocabularyScope.valueOf(scopeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Unknown scope: " + scopeStr + ". Valid: GLOBAL, ORG, TEAM, PERSONAL"))
                    .build();
        }

        // Only GLOBAL vocabulary is currently supported.
        // ORG/TEAM/PERSONAL require an authentication context and multi-tenant vocabulary creation
        // which is deferred to a later milestone.
        if (scope != VocabularyScope.GLOBAL) {
            return Response.status(501)
                    .entity(Map.of(
                            "error", "Only GLOBAL vocabulary is currently supported. " +
                                    "ORG/TEAM/PERSONAL vocabulary creation requires authentication context " +
                                    "(deferred milestone)."))
                    .build();
        }

        final var globalVocab = vocabularyService.findGlobalVocabulary();
        if (globalVocab == null) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "GLOBAL vocabulary not found — check Flyway V3 migration"))
                    .build();
        }

        final LabelDefinition def = vocabularyService.addDefinition(
                globalVocab.id, request.path(), request.description(),
                request.addedBy() != null ? request.addedBy() : "unknown");

        return Response.status(Response.Status.CREATED)
                .entity(Map.of("id", def.id, "path", def.path, "scope", VocabularyScope.GLOBAL))
                .build();
    }
}
