package io.casehub.work.runtime.model;

/**
 * Determines how a label was applied to a WorkItem and how it is maintained.
 *
 * <p>
 * {@code MANUAL} labels are applied by humans and persist until explicitly removed.
 * {@code INFERRED} labels are applied by the filter engine and are recomputed on every
 * WorkItem mutation — they exist only while the filter condition remains true.
 */
public enum LabelPersistence {
    /**
     * Human-applied. Only removed by an explicit API call or human action.
     * Never touched by the filter re-evaluation cycle.
     */
    MANUAL,

    /**
     * Filter-applied. Stripped and recomputed on every WorkItem mutation.
     * Exists only while at least one FilterChain supports it.
     */
    INFERRED
}
