package io.quarkiverse.tarkus.ledger.model;

/**
 * Classifies a {@link LedgerEntry} by the kind of record it represents.
 *
 * <ul>
 * <li>{@code COMMAND} — the intent expressed by an actor before execution</li>
 * <li>{@code EVENT} — the observable fact after a WorkItem transition</li>
 * <li>{@code ATTESTATION} — a peer judgment on an existing ledger entry</li>
 * </ul>
 */
public enum LedgerEntryType {
    COMMAND,
    EVENT,
    ATTESTATION
}
