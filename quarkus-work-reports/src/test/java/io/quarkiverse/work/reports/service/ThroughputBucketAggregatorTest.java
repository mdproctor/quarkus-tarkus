package io.quarkiverse.work.reports.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

class ThroughputBucketAggregatorTest {

    @Test
    void groupByDay_sameDay_oneBucket() {
        LocalDate day = LocalDate.of(2026, 4, 15);
        List<Object[]> created = List.<Object[]> of(new Object[] { day, 3L });
        List<Object[]> completed = List.<Object[]> of(new Object[] { day, 2L });

        List<ThroughputBucket> buckets = ThroughputBucketAggregator.aggregate(created, completed, "day");

        assertThat(buckets).hasSize(1);
        assertThat(buckets.get(0).period()).isEqualTo("2026-04-15");
        assertThat(buckets.get(0).created()).isEqualTo(3L);
        assertThat(buckets.get(0).completed()).isEqualTo(2L);
    }

    @Test
    void groupByDay_twoDays_twoBuckets() {
        List<Object[]> created = List.<Object[]> of(
                new Object[] { LocalDate.of(2026, 4, 15), 2L },
                new Object[] { LocalDate.of(2026, 4, 16), 5L });
        List<Object[]> completed = List.<Object[]> of(
                new Object[] { LocalDate.of(2026, 4, 15), 1L });

        List<ThroughputBucket> buckets = ThroughputBucketAggregator.aggregate(created, completed, "day");

        assertThat(buckets).hasSize(2);
        assertThat(buckets.get(0).period()).isEqualTo("2026-04-15");
        assertThat(buckets.get(0).created()).isEqualTo(2L);
        assertThat(buckets.get(0).completed()).isEqualTo(1L);
        assertThat(buckets.get(1).period()).isEqualTo("2026-04-16");
        assertThat(buckets.get(1).created()).isEqualTo(5L);
        assertThat(buckets.get(1).completed()).isEqualTo(0L);
    }

    @Test
    void groupByWeek_sameIsoWeek_oneBucket() {
        // 2026-04-13 (Mon) and 2026-04-19 (Sun) are both in ISO week 16
        List<Object[]> created = List.<Object[]> of(
                new Object[] { LocalDate.of(2026, 4, 13), 3L },
                new Object[] { LocalDate.of(2026, 4, 19), 2L });

        List<ThroughputBucket> buckets = ThroughputBucketAggregator.aggregate(created, List.of(), "week");

        assertThat(buckets).hasSize(1);
        assertThat(buckets.get(0).period()).isEqualTo("2026-W16");
        assertThat(buckets.get(0).created()).isEqualTo(5L);
    }

    @Test
    void groupByWeek_differentWeeks_twoBuckets() {
        List<Object[]> created = List.<Object[]> of(
                new Object[] { LocalDate.of(2026, 4, 13), 1L }, // W16
                new Object[] { LocalDate.of(2026, 4, 20), 1L } // W17
        );

        List<ThroughputBucket> buckets = ThroughputBucketAggregator.aggregate(created, List.of(), "week");

        assertThat(buckets).hasSize(2);
        assertThat(buckets.get(0).period()).isEqualTo("2026-W16");
        assertThat(buckets.get(1).period()).isEqualTo("2026-W17");
    }

    @Test
    void groupByMonth_sameMonth_oneBucket() {
        List<Object[]> created = List.<Object[]> of(
                new Object[] { LocalDate.of(2026, 4, 1), 4L },
                new Object[] { LocalDate.of(2026, 4, 30), 6L });
        List<Object[]> completed = List.<Object[]> of(
                new Object[] { LocalDate.of(2026, 4, 15), 3L });

        List<ThroughputBucket> buckets = ThroughputBucketAggregator.aggregate(created, completed, "month");

        assertThat(buckets).hasSize(1);
        assertThat(buckets.get(0).period()).isEqualTo("2026-04");
        assertThat(buckets.get(0).created()).isEqualTo(10L);
        assertThat(buckets.get(0).completed()).isEqualTo(3L);
    }

    @Test
    void groupByMonth_differentMonths_twoBuckets() {
        List<Object[]> created = List.<Object[]> of(
                new Object[] { LocalDate.of(2026, 3, 31), 2L },
                new Object[] { LocalDate.of(2026, 4, 1), 3L });

        List<ThroughputBucket> buckets = ThroughputBucketAggregator.aggregate(created, List.of(), "month");

        assertThat(buckets).hasSize(2);
        assertThat(buckets.get(0).period()).isEqualTo("2026-03");
        assertThat(buckets.get(1).period()).isEqualTo("2026-04");
    }

    @Test
    void emptyInput_returnsEmptyList() {
        List<ThroughputBucket> buckets = ThroughputBucketAggregator.aggregate(List.of(), List.of(), "day");
        assertThat(buckets).isEmpty();
    }

    @Test
    void completedOnlyDay_createdIsZero() {
        List<Object[]> completed = List.<Object[]> of(new Object[] { LocalDate.of(2026, 4, 15), 5L });
        List<ThroughputBucket> buckets = ThroughputBucketAggregator.aggregate(List.of(), completed, "day");

        assertThat(buckets).hasSize(1);
        assertThat(buckets.get(0).created()).isEqualTo(0L);
        assertThat(buckets.get(0).completed()).isEqualTo(5L);
    }

    @Test
    void bucketsAreSortedByPeriod() {
        List<Object[]> created = List.<Object[]> of(
                new Object[] { LocalDate.of(2026, 4, 17), 1L },
                new Object[] { LocalDate.of(2026, 4, 14), 1L },
                new Object[] { LocalDate.of(2026, 4, 15), 1L });
        List<ThroughputBucket> buckets = ThroughputBucketAggregator.aggregate(created, List.of(), "day");

        assertThat(buckets).extracting(ThroughputBucket::period)
                .containsExactly("2026-04-14", "2026-04-15", "2026-04-17");
    }
}
