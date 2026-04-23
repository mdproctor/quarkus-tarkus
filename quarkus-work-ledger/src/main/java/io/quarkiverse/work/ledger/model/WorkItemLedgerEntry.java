package io.quarkiverse.work.ledger.model;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import io.quarkiverse.ledger.runtime.model.LedgerEntry;

/**
 * A ledger entry scoped to a single WorkItem lifecycle transition.
 *
 * <p>
 * Extends the domain-agnostic {@link LedgerEntry} base class using JPA JOINED
 * inheritance. The {@code work_item_ledger_entry} table holds WorkItem-specific
 * fields; all common fields live in {@code ledger_entry}. The {@code subjectId}
 * field on the base class carries the WorkItem UUID.
 *
 * <p>
 * The {@code commandType} and {@code eventType} fields encode the CQRS
 * command/event separation for each lifecycle transition — e.g.
 * {@code "CompleteWorkItem"} / {@code "WorkItemCompleted"}.
 */
@Entity
@Table(name = "work_item_ledger_entry")
@DiscriminatorValue("WORK_ITEM")
public class WorkItemLedgerEntry extends LedgerEntry {

    /** The actor's expressed intent — e.g. {@code "CompleteWorkItem"}. Nullable. */
    @Column(name = "command_type")
    public String commandType;

    /** The observable fact after execution — e.g. {@code "WorkItemCompleted"}. Nullable. */
    @Column(name = "event_type")
    public String eventType;
}
