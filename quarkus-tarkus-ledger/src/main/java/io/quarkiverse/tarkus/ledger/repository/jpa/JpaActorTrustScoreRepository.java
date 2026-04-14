package io.quarkiverse.tarkus.ledger.repository.jpa;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.tarkus.ledger.model.ActorTrustScore;
import io.quarkiverse.tarkus.ledger.model.ActorType;
import io.quarkiverse.tarkus.ledger.repository.ActorTrustScoreRepository;

/**
 * Hibernate ORM / Panache implementation of {@link ActorTrustScoreRepository}.
 */
@ApplicationScoped
public class JpaActorTrustScoreRepository implements ActorTrustScoreRepository {

    /** {@inheritDoc} */
    @Override
    public Optional<ActorTrustScore> findByActorId(final String actorId) {
        return Optional.ofNullable(ActorTrustScore.findById(actorId));
    }

    /** {@inheritDoc} */
    @Override
    public void upsert(final String actorId, final ActorType actorType, final double trustScore,
            final int decisionCount, final int overturnedCount, final int appealCount,
            final int attestationPositive, final int attestationNegative,
            final Instant lastComputedAt) {
        ActorTrustScore score = ActorTrustScore.findById(actorId);
        if (score == null) {
            score = new ActorTrustScore();
            score.actorId = actorId;
        }
        score.actorType = actorType;
        score.trustScore = trustScore;
        score.decisionCount = decisionCount;
        score.overturnedCount = overturnedCount;
        score.appealCount = appealCount;
        score.attestationPositive = attestationPositive;
        score.attestationNegative = attestationNegative;
        score.lastComputedAt = lastComputedAt;
        score.persist();
    }

    /** {@inheritDoc} */
    @Override
    public List<ActorTrustScore> findAll() {
        return ActorTrustScore.listAll();
    }
}
