package io.quarkiverse.tarkus.ledger.model;

/**
 * Classifies the kind of actor that triggered or attested a ledger entry.
 *
 * <ul>
 * <li>{@code HUMAN} — a person acting through a UI or REST call</li>
 * <li>{@code AGENT} — an autonomous software agent (e.g. Qhorus)</li>
 * <li>{@code SYSTEM} — the Tarkus system itself (expiry, escalation)</li>
 * </ul>
 */
public enum ActorType {
    HUMAN,
    AGENT,
    SYSTEM
}
