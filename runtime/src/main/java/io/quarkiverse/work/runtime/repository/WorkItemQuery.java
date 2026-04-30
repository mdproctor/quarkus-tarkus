package io.casehub.work.runtime.repository;

import java.time.Instant;
import java.util.List;

import io.casehub.work.runtime.model.WorkItemPriority;
import io.casehub.work.runtime.model.WorkItemStatus;

/**
 * KV-native query criteria for {@link WorkItemStore#scan}.
 *
 * <p>
 * Replaces the individual query methods on the former {@code WorkItemRepository}
 * ({@code findInbox}, {@code findExpired}, {@code findByLabelPattern}, etc.)
 * with a single composable value object. Backends translate this to their
 * native query language (SQL, MongoDB aggregation, Redis set operations, etc.).
 *
 * <h2>Semantics</h2>
 * <p>
 * Assignment fields ({@code assigneeId}, {@code candidateGroups},
 * {@code candidateUserId}) are combined with <b>OR</b> logic — an item matches
 * if it satisfies any one of the non-null assignment criteria.
 *
 * <p>
 * All other fields ({@code status}, {@code statusIn}, {@code priority},
 * {@code category}, {@code followUpBefore}, {@code expiresAtOrBefore},
 * {@code claimDeadlineOrBefore}, {@code labelPattern}) are combined with
 * <b>AND</b> logic on top of the assignment match. A {@code null} value means
 * "no constraint on this dimension".
 *
 * <h2>Common patterns</h2>
 *
 * <pre>
 * // Inbox for alice in finance-team, HIGH priority only
 * WorkItemQuery.inbox("alice", List.of("finance-team"), null)
 *         .toBuilder().priority(WorkItemPriority.HIGH).build();
 *
 * // All WorkItems with an expired completion deadline
 * WorkItemQuery.expired(Instant.now());
 *
 * // WorkItems matching a label pattern
 * WorkItemQuery.byLabelPattern("legal/**");
 * </pre>
 */
public final class WorkItemQuery {

    // Assignment — OR-combined
    private final String assigneeId;
    private final List<String> candidateGroups;
    private final String candidateUserId;

    // Filters — AND-combined
    private final WorkItemStatus status;
    private final List<WorkItemStatus> statusIn;
    private final WorkItemPriority priority;
    private final String category;
    private final Instant followUpBefore;

    // Time predicates
    private final Instant expiresAtOrBefore;
    private final Instant claimDeadlineOrBefore;

    // Label
    private final String labelPattern;

    private WorkItemQuery(final Builder b) {
        this.assigneeId = b.assigneeId;
        this.candidateGroups = b.candidateGroups;
        this.candidateUserId = b.candidateUserId;
        this.status = b.status;
        this.statusIn = b.statusIn;
        this.priority = b.priority;
        this.category = b.category;
        this.followUpBefore = b.followUpBefore;
        this.expiresAtOrBefore = b.expiresAtOrBefore;
        this.claimDeadlineOrBefore = b.claimDeadlineOrBefore;
        this.labelPattern = b.labelPattern;
    }

    // ── Static factories for common patterns ─────────────────────────────────

    /** No constraints — returns all WorkItems. Admin use only. */
    public static WorkItemQuery all() {
        return new Builder().build();
    }

    /**
     * Inbox query: items visible to the given actor via any assignment dimension.
     * All three parameters are nullable; at least one should be non-null.
     *
     * @param assigneeId the direct assignee identifier; may be {@code null}
     * @param candidateGroups groups the actor belongs to; may be {@code null} or empty
     * @param candidateUserId user listed in candidateUsers; may be {@code null}
     * @return query matching items visible via any assignment dimension
     */
    public static WorkItemQuery inbox(final String assigneeId,
            final List<String> candidateGroups, final String candidateUserId) {
        return new Builder()
                .assigneeId(assigneeId)
                .candidateGroups(candidateGroups)
                .candidateUserId(candidateUserId)
                .build();
    }

    /**
     * Items whose {@code expiresAt} is on or before {@code now} and whose
     * status is one of the active (non-terminal) statuses.
     *
     * @param now the reference instant to compare against {@code expiresAt}
     * @return query matching expired active work items
     */
    public static WorkItemQuery expired(final Instant now) {
        return new Builder()
                .expiresAtOrBefore(now)
                .statusIn(List.of(
                        WorkItemStatus.PENDING,
                        WorkItemStatus.ASSIGNED,
                        WorkItemStatus.IN_PROGRESS,
                        WorkItemStatus.SUSPENDED))
                .build();
    }

    /**
     * Items whose {@code claimDeadline} is on or before {@code now} and whose
     * status is {@link WorkItemStatus#PENDING}.
     *
     * @param now the reference instant to compare against {@code claimDeadline}
     * @return query matching pending work items past their claim deadline
     */
    public static WorkItemQuery claimExpired(final Instant now) {
        return new Builder()
                .claimDeadlineOrBefore(now)
                .status(WorkItemStatus.PENDING)
                .build();
    }

    /**
     * Items with at least one label matching the given pattern (exact / {@code *} / {@code **}).
     *
     * @param pattern the label pattern to match against; must not be null
     * @return query matching work items with a label satisfying the pattern
     */
    public static WorkItemQuery byLabelPattern(final String pattern) {
        return new Builder().labelPattern(pattern).build();
    }

    // ── Accessors ────────────────────────────────────────────────────────────

    /**
     * Returns the direct assignee filter, or {@code null} if not constrained.
     *
     * @return assignee id filter value
     */
    public String assigneeId() {
        return assigneeId;
    }

    /**
     * Returns the candidate groups filter, or {@code null} if not constrained.
     *
     * @return candidate groups filter value
     */
    public List<String> candidateGroups() {
        return candidateGroups;
    }

    /**
     * Returns the candidate user id filter, or {@code null} if not constrained.
     *
     * @return candidate user id filter value
     */
    public String candidateUserId() {
        return candidateUserId;
    }

    /**
     * Returns the exact status filter, or {@code null} if not constrained.
     *
     * @return status filter value
     */
    public WorkItemStatus status() {
        return status;
    }

    /**
     * Returns the status-in filter, or {@code null} if not constrained.
     *
     * @return list of acceptable statuses
     */
    public List<WorkItemStatus> statusIn() {
        return statusIn;
    }

    /**
     * Returns the priority filter, or {@code null} if not constrained.
     *
     * @return priority filter value
     */
    public WorkItemPriority priority() {
        return priority;
    }

    /**
     * Returns the category filter, or {@code null} if not constrained.
     *
     * @return category filter value
     */
    public String category() {
        return category;
    }

    /**
     * Returns the follow-up-before filter, or {@code null} if not constrained.
     *
     * @return follow-up-before instant
     */
    public Instant followUpBefore() {
        return followUpBefore;
    }

    /**
     * Returns the expires-at-or-before filter, or {@code null} if not constrained.
     *
     * @return expires-at-or-before instant
     */
    public Instant expiresAtOrBefore() {
        return expiresAtOrBefore;
    }

    /**
     * Returns the claim-deadline-or-before filter, or {@code null} if not constrained.
     *
     * @return claim-deadline-or-before instant
     */
    public Instant claimDeadlineOrBefore() {
        return claimDeadlineOrBefore;
    }

    /**
     * Returns the label pattern filter, or {@code null} if not constrained.
     *
     * @return label pattern string
     */
    public String labelPattern() {
        return labelPattern;
    }

    /** Returns a builder pre-populated with this query's values for incremental modification. */
    public Builder toBuilder() {
        return new Builder()
                .assigneeId(assigneeId)
                .candidateGroups(candidateGroups)
                .candidateUserId(candidateUserId)
                .status(status)
                .statusIn(statusIn)
                .priority(priority)
                .category(category)
                .followUpBefore(followUpBefore)
                .expiresAtOrBefore(expiresAtOrBefore)
                .claimDeadlineOrBefore(claimDeadlineOrBefore)
                .labelPattern(labelPattern);
    }

    /**
     * Returns a new empty builder.
     *
     * @return a fresh builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    // ── Builder ───────────────────────────────────────────────────────────────

    /** Builder for {@link WorkItemQuery}. */
    public static final class Builder {

        private String assigneeId;
        private List<String> candidateGroups;
        private String candidateUserId;
        private WorkItemStatus status;
        private List<WorkItemStatus> statusIn;
        private WorkItemPriority priority;
        private String category;
        private Instant followUpBefore;
        private Instant expiresAtOrBefore;
        private Instant claimDeadlineOrBefore;
        private String labelPattern;

        /**
         * Sets the assignee id constraint.
         *
         * @param v the assignee id; {@code null} means unconstrained
         * @return this builder
         */
        public Builder assigneeId(final String v) {
            this.assigneeId = v;
            return this;
        }

        /**
         * Sets the candidate groups constraint.
         *
         * @param v the candidate groups; {@code null} means unconstrained
         * @return this builder
         */
        public Builder candidateGroups(final List<String> v) {
            this.candidateGroups = v;
            return this;
        }

        /**
         * Sets the candidate user id constraint.
         *
         * @param v the candidate user id; {@code null} means unconstrained
         * @return this builder
         */
        public Builder candidateUserId(final String v) {
            this.candidateUserId = v;
            return this;
        }

        /**
         * Sets the exact status constraint.
         *
         * @param v the status; {@code null} means unconstrained
         * @return this builder
         */
        public Builder status(final WorkItemStatus v) {
            this.status = v;
            return this;
        }

        /**
         * Sets the status-in constraint.
         *
         * @param v the list of acceptable statuses; {@code null} means unconstrained
         * @return this builder
         */
        public Builder statusIn(final List<WorkItemStatus> v) {
            this.statusIn = v;
            return this;
        }

        /**
         * Sets the priority constraint.
         *
         * @param v the priority; {@code null} means unconstrained
         * @return this builder
         */
        public Builder priority(final WorkItemPriority v) {
            this.priority = v;
            return this;
        }

        /**
         * Sets the category constraint.
         *
         * @param v the category; {@code null} means unconstrained
         * @return this builder
         */
        public Builder category(final String v) {
            this.category = v;
            return this;
        }

        /**
         * Sets the follow-up-before constraint.
         *
         * @param v the instant; {@code null} means unconstrained
         * @return this builder
         */
        public Builder followUpBefore(final Instant v) {
            this.followUpBefore = v;
            return this;
        }

        /**
         * Sets the expires-at-or-before constraint.
         *
         * @param v the instant; {@code null} means unconstrained
         * @return this builder
         */
        public Builder expiresAtOrBefore(final Instant v) {
            this.expiresAtOrBefore = v;
            return this;
        }

        /**
         * Sets the claim-deadline-or-before constraint.
         *
         * @param v the instant; {@code null} means unconstrained
         * @return this builder
         */
        public Builder claimDeadlineOrBefore(final Instant v) {
            this.claimDeadlineOrBefore = v;
            return this;
        }

        /**
         * Sets the label pattern constraint.
         *
         * @param v the pattern; {@code null} means unconstrained
         * @return this builder
         */
        public Builder labelPattern(final String v) {
            this.labelPattern = v;
            return this;
        }

        /**
         * Builds the {@link WorkItemQuery}.
         *
         * @return a new immutable query instance
         */
        public WorkItemQuery build() {
            return new WorkItemQuery(this);
        }
    }
}
