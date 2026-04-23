package io.quarkiverse.work.runtime.repository;

import java.time.Instant;

/**
 * Immutable query value object for cross-WorkItem audit history searches.
 *
 * <p>
 * All filters are optional — omitting a field means "no constraint on that dimension".
 * Combine freely: actorId + event + date range is a valid query.
 *
 * <p>
 * Pagination uses zero-based page numbering with a default page size of 20,
 * capped at 100 per request to prevent accidental large fetches.
 *
 * <p>
 * Built via {@link #builder()} or the {@link #all()} convenience factory.
 *
 * @see <a href="https://github.com/mdproctor/quarkus-work/issues/109">Issue #109</a>
 * @see <a href="https://github.com/mdproctor/quarkus-work/issues/99">Epic #99</a>
 */
public final class AuditQuery {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final String actorId;
    private final Instant from;
    private final Instant to;
    private final String event;
    private final String category;
    private final int page;
    private final int size;

    private AuditQuery(final Builder b) {
        this.actorId = b.actorId;
        this.from = b.from;
        this.to = b.to;
        this.event = b.event;
        this.category = b.category;
        this.page = Math.max(0, b.page);
        this.size = Math.min(MAX_SIZE, Math.max(1, b.size));
    }

    /** Returns an unconstrained query using default pagination (page 0, size 20). */
    public static AuditQuery all() {
        return builder().build();
    }

    /** Returns a fresh builder for constructing a constrained query. */
    public static Builder builder() {
        return new Builder();
    }

    public String actorId() {
        return actorId;
    }

    public Instant from() {
        return from;
    }

    public Instant to() {
        return to;
    }

    public String event() {
        return event;
    }

    public String category() {
        return category;
    }

    public int page() {
        return page;
    }

    public int size() {
        return size;
    }

    /** Fluent builder for {@link AuditQuery}. */
    public static final class Builder {

        private String actorId;
        private Instant from;
        private Instant to;
        private String event;
        private String category;
        private int page = 0;
        private int size = DEFAULT_SIZE;

        private Builder() {
        }

        /** Filter by exact actor identifier. */
        public Builder actorId(final String actorId) {
            this.actorId = actorId;
            return this;
        }

        /** Include only entries at or after this instant (inclusive). */
        public Builder from(final Instant from) {
            this.from = from;
            return this;
        }

        /** Include only entries at or before this instant (inclusive). */
        public Builder to(final Instant to) {
            this.to = to;
            return this;
        }

        /** Filter by exact event type (e.g. "CREATED", "COMPLETED"). */
        public Builder event(final String event) {
            this.event = event;
            return this;
        }

        /**
         * Filter entries to WorkItems whose category matches this value.
         * Implemented as a subquery on work_item — no JOIN in the main query.
         */
        public Builder category(final String category) {
            this.category = category;
            return this;
        }

        /** Zero-based page number (negative values treated as 0). */
        public Builder page(final int page) {
            this.page = page;
            return this;
        }

        /** Page size (capped at 100, minimum 1). */
        public Builder size(final int size) {
            this.size = size;
            return this;
        }

        public AuditQuery build() {
            return new AuditQuery(this);
        }
    }
}
