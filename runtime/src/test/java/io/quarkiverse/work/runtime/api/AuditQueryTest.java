package io.quarkiverse.work.runtime.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;

import io.quarkiverse.work.runtime.repository.AuditQuery;

/**
 * Unit tests for AuditQuery value object — no Quarkus boot needed.
 *
 * <p>
 * Issue #109, Epic #99.
 */
class AuditQueryTest {

    @Test
    void defaultQuery_hasNullFilters_andDefaultPagination() {
        final AuditQuery q = AuditQuery.all();
        assertThat(q.actorId()).isNull();
        assertThat(q.from()).isNull();
        assertThat(q.to()).isNull();
        assertThat(q.event()).isNull();
        assertThat(q.category()).isNull();
        assertThat(q.page()).isZero();
        assertThat(q.size()).isEqualTo(20);
    }

    @Test
    void builder_setsAllFilters() {
        final Instant from = Instant.now().minus(7, ChronoUnit.DAYS);
        final Instant to = Instant.now();

        final AuditQuery q = AuditQuery.builder()
                .actorId("alice")
                .from(from)
                .to(to)
                .event("COMPLETED")
                .category("finance")
                .page(2)
                .size(50)
                .build();

        assertThat(q.actorId()).isEqualTo("alice");
        assertThat(q.from()).isEqualTo(from);
        assertThat(q.to()).isEqualTo(to);
        assertThat(q.event()).isEqualTo("COMPLETED");
        assertThat(q.category()).isEqualTo("finance");
        assertThat(q.page()).isEqualTo(2);
        assertThat(q.size()).isEqualTo(50);
    }

    @Test
    void builder_actorOnly() {
        final AuditQuery q = AuditQuery.builder().actorId("bob").build();
        assertThat(q.actorId()).isEqualTo("bob");
        assertThat(q.event()).isNull();
        assertThat(q.category()).isNull();
    }

    @Test
    void builder_eventOnly() {
        final AuditQuery q = AuditQuery.builder().event("CREATED").build();
        assertThat(q.event()).isEqualTo("CREATED");
        assertThat(q.actorId()).isNull();
    }

    @Test
    void builder_dateRangeOnly() {
        final Instant from = Instant.parse("2026-01-01T00:00:00Z");
        final Instant to = Instant.parse("2026-03-31T23:59:59Z");
        final AuditQuery q = AuditQuery.builder().from(from).to(to).build();
        assertThat(q.from()).isEqualTo(from);
        assertThat(q.to()).isEqualTo(to);
        assertThat(q.actorId()).isNull();
    }

    @Test
    void pageAndSize_defaultToFirstPage20() {
        final AuditQuery q = AuditQuery.builder().actorId("x").build();
        assertThat(q.page()).isZero();
        assertThat(q.size()).isEqualTo(20);
    }

    @Test
    void size_isCapppedAt100() {
        final AuditQuery q = AuditQuery.builder().size(999).build();
        assertThat(q.size()).isLessThanOrEqualTo(100);
    }
}
