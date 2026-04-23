package io.quarkiverse.work.runtime.model;

import java.util.Map;

/**
 * Well-known relation type constants for {@link WorkItemRelation}.
 *
 * <h2>Extensibility — this is not an enum</h2>
 * <p>
 * Relation types are plain strings stored in the {@code relation_type} column.
 * This class provides named constants for discoverability, but consuming
 * applications can use <em>any non-blank string</em> as a relation type without
 * any schema change, registration, or configuration:
 *
 * <pre>{@code
 * // Well-known type — use the constant
 * "PART_OF"
 *
 * // Custom type — just use the string
 * "TRIGGERED_BY"
 * "APPROVED_BY"
 * "ESCALATED_FROM"
 * "RESOLVED_BY"
 * }</pre>
 *
 * <h2>Directionality</h2>
 * <p>
 * All relations are directed: {@code source → target}.
 * <ul>
 * <li>{@code "child" PART_OF "parent"} — child is a member of the parent group</li>
 * <li>{@code "A" BLOCKS "B"} — A must be resolved before B can proceed</li>
 * <li>{@code "A" RELATES_TO "B"} — bidirectional by convention; store two rows if both
 * directions are needed</li>
 * </ul>
 *
 * <h2>UI implications</h2>
 * <p>
 * {@link #PART_OF} enables:
 * <ul>
 * <li>Tree navigation — traverse parent and children via REST</li>
 * <li>Group summaries — X of Y children completed</li>
 * <li>Breadcrumb trails — walk PART_OF chain to root</li>
 * <li>"Top-level only" filter — WorkItems with no outgoing PART_OF</li>
 * <li>Epic-style views — one root WorkItem with a child list</li>
 * </ul>
 */
public final class WorkItemRelationType {

    private WorkItemRelationType() {
    }

    /**
     * The source WorkItem is a component of the target WorkItem.
     * Directed: {@code child → parent}.
     *
     * <p>
     * Cycle prevention is enforced at the application layer for this type —
     * a WorkItem cannot be its own ancestor.
     */
    public static final String PART_OF = "PART_OF";

    /**
     * The source WorkItem must be resolved before the target can proceed.
     * Inverse of {@link #BLOCKED_BY}.
     */
    public static final String BLOCKS = "BLOCKS";

    /**
     * The source WorkItem cannot proceed until the target is resolved.
     * Inverse of {@link #BLOCKS}.
     */
    public static final String BLOCKED_BY = "BLOCKED_BY";

    /**
     * The source and target WorkItems are contextually related.
     * Symmetric by convention — store two rows for a true bidirectional link.
     */
    public static final String RELATES_TO = "RELATES_TO";

    /**
     * The source WorkItem is a duplicate of the target.
     * The source is typically closed and the target kept as the canonical item.
     */
    public static final String DUPLICATES = "DUPLICATES";

    // Known inverse pairs — used by inverse() to navigate the graph both ways.
    private static final Map<String, String> INVERSES = Map.of(
            BLOCKS, BLOCKED_BY,
            BLOCKED_BY, BLOCKS);

    /**
     * Return the semantic inverse of the given relation type, if one is defined.
     *
     * <p>
     * Only applies to asymmetric pairs ({@link #BLOCKS} ↔ {@link #BLOCKED_BY}).
     * Returns {@code null} for types with no defined inverse ({@link #PART_OF},
     * {@link #RELATES_TO}, {@link #DUPLICATES}, and all custom types).
     *
     * @param relationType the relation type to invert
     * @return the inverse type, or {@code null} if none is defined
     */
    public static String inverse(final String relationType) {
        return INVERSES.get(relationType);
    }
}
