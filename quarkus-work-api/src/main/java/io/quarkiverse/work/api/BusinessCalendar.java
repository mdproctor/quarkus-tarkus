package io.casehub.work.api;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/**
 * SPI for business-hours-aware deadline calculation.
 *
 * <p>
 * Real SLAs are defined in business hours — "48 hours" means 48 working hours,
 * not 48 wall-clock hours. Implementations advance an {@link Instant} by a given
 * {@link Duration}, counting only time that falls within business windows and
 * skipping weekends, holidays, and out-of-hours periods.
 *
 * <p>
 * The default implementation is driven by {@code casehub.work.business-hours.*}
 * config. Override by providing a CDI {@code @ApplicationScoped} bean that
 * implements this interface.
 *
 * <p>
 * Used by {@code WorkItemService} to resolve {@code expiresAtBusinessHours} and
 * {@code claimDeadlineBusinessHours} fields to absolute {@link Instant} values
 * at WorkItem creation time.
 */
public interface BusinessCalendar {

    /**
     * Calculate the {@link Instant} that is {@code businessDuration} of business
     * time after {@code start}, in the given {@link ZoneId}.
     *
     * <p>
     * Non-business hours, weekends, and holidays are skipped entirely — the clock
     * only ticks during business windows.
     *
     * <p>
     * Example: {@code start} = Friday 16:00 (Europe/London), {@code businessDuration}
     * = 2 hours, business window 09:00–17:00 Mon–Fri → result = Monday 11:00.
     *
     * @param start the starting instant (wall clock)
     * @param businessDuration the amount of business time to advance
     * @param zone the timezone in which business hours are defined
     * @return the instant when {@code businessDuration} of business time has elapsed
     */
    Instant addBusinessDuration(Instant start, Duration businessDuration, ZoneId zone);

    /**
     * Returns {@code true} if the given instant falls within a business hour window
     * (not a weekend, not a holiday, within the configured daily start/end times).
     *
     * <p>
     * Used to determine whether a scheduled deadline check should fire immediately
     * or wait for the next business window.
     *
     * @param instant the instant to test
     * @param zone the timezone in which business hours are defined
     * @return {@code true} if {@code instant} is a business hour
     */
    boolean isBusinessHour(Instant instant, ZoneId zone);
}
