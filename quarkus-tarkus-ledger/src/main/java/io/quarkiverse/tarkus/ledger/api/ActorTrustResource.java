package io.quarkiverse.tarkus.ledger.api;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;

import io.quarkiverse.tarkus.ledger.api.dto.ActorTrustScoreResponse;
import io.quarkiverse.tarkus.ledger.config.LedgerConfig;
import io.quarkiverse.tarkus.ledger.model.ActorTrustScore;
import io.quarkiverse.tarkus.ledger.repository.ActorTrustScoreRepository;

/**
 * REST endpoints for actor trust scores.
 *
 * <p>
 * All endpoints are under {@code /tarkus/actors} and are only active when
 * {@code quarkus.tarkus.ledger.trust-score.enabled=true}.
 */
@Path("/tarkus/actors")
@Produces(APPLICATION_JSON)
@ApplicationScoped
public class ActorTrustResource {

    @Inject
    ActorTrustScoreRepository trustRepo;

    @Inject
    LedgerConfig config;

    /**
     * Retrieve the computed trust score for a specific actor.
     *
     * <p>
     * Returns 404 if trust scoring is disabled or if no score has been computed yet
     * for the given actor.
     *
     * @param actorId the actor's identity string
     * @return 200 OK with the trust score, or 404 if not found / disabled
     */
    @GET
    @Path("/{actorId}/trust")
    public Response getActorTrust(@PathParam("actorId") final String actorId) {
        if (!config.trustScore().enabled()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "Trust scoring is not enabled"))
                    .build();
        }
        return trustRepo.findByActorId(actorId)
                .map(score -> Response.ok(toResponse(score)).build())
                .orElse(Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "No trust score computed for: " + actorId))
                        .build());
    }

    private ActorTrustScoreResponse toResponse(final ActorTrustScore s) {
        return new ActorTrustScoreResponse(s.actorId, s.actorType, s.trustScore,
                s.decisionCount, s.overturnedCount, s.attestationPositive,
                s.attestationNegative, s.lastComputedAt);
    }
}
