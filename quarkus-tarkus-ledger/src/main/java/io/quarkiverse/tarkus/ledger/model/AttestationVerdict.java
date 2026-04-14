package io.quarkiverse.tarkus.ledger.model;

/**
 * The verdict recorded by a peer attesting a {@link LedgerAttestation}.
 *
 * <ul>
 * <li>{@code SOUND} — the decision was correct and well-reasoned</li>
 * <li>{@code FLAGGED} — the decision warrants further review</li>
 * <li>{@code ENDORSED} — the attestor positively endorses the decision</li>
 * <li>{@code CHALLENGED} — the attestor disputes the decision</li>
 * </ul>
 */
public enum AttestationVerdict {
    SOUND,
    FLAGGED,
    ENDORSED,
    CHALLENGED
}
