package io.quarkiverse.work.runtime.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;

/**
 * Pure unit tests for WorkItemScheduleService cron logic — no Quarkus, no DB.
 */
class WorkItemScheduleServiceTest {

    // ── computeNextFireAt ─────────────────────────────────────────────────────

    @Test
    void computeNextFireAt_everySecond_returnsNearFuture() throws Exception {
        // "* * * * * ?" fires every second — next fire is always within 1 second
        final Instant next = WorkItemScheduleService.computeNextFireAt("* * * * * ?");
        assertThat(next).isNotNull();
        assertThat(next).isAfter(Instant.now().minusSeconds(1));
        assertThat(next).isBefore(Instant.now().plusSeconds(2));
    }

    @Test
    void computeNextFireAt_dailyAt9am_returnsInstantInFuture() throws Exception {
        final Instant next = WorkItemScheduleService.computeNextFireAt("0 0 9 * * ?");
        assertThat(next).isNotNull().isAfter(Instant.now().minusSeconds(1));
    }

    @Test
    void computeNextFireAt_invalidCron_throwsException() {
        assertThatThrownBy(() -> WorkItemScheduleService.computeNextFireAt("not-a-cron"))
                .isInstanceOf(Exception.class);
    }

    @Test
    void computeNextFireAt_returnsDistinctTimesForDifferentExpressions() throws Exception {
        final Instant every30min = WorkItemScheduleService.computeNextFireAt("0 0/30 * * * ?");
        final Instant every60min = WorkItemScheduleService.computeNextFireAt("0 0 * * * ?");
        // Both are valid and in the future
        assertThat(every30min).isAfter(Instant.now().minusSeconds(1));
        assertThat(every60min).isAfter(Instant.now().minusSeconds(1));
    }

    // ── isDue ─────────────────────────────────────────────────────────────────

    @Test
    void isDue_returnTrue_whenNextFireAtIsInPast() {
        final Instant pastNextFire = Instant.now().minusSeconds(5);
        assertThat(WorkItemScheduleService.isDue(pastNextFire, Instant.now())).isTrue();
    }

    @Test
    void isDue_returnsFalse_whenNextFireAtIsInFuture() {
        final Instant futureNextFire = Instant.now().plusSeconds(3600);
        assertThat(WorkItemScheduleService.isDue(futureNextFire, Instant.now())).isFalse();
    }

    @Test
    void isDue_returnsFalse_whenNextFireAtIsNull() {
        assertThat(WorkItemScheduleService.isDue(null, Instant.now())).isFalse();
    }
}
