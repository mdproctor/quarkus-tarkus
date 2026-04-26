package io.quarkiverse.work.api;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * SPI for holiday data — an optional sub-SPI consumed by {@link BusinessCalendar}
 * implementations to skip public holidays when advancing business time.
 *
 * <p>
 * Provide a CDI {@code @ApplicationScoped} bean implementing this interface to
 * plug in any holiday source: a static config list, an iCal feed, a database,
 * or an external API. The default implementation reads from
 * {@code quarkus.work.business-hours.holidays}.
 *
 * <p>
 * An optional iCal-backed implementation activates automatically when
 * {@code quarkus.work.business-hours.holiday-ical-url} is configured.
 */
public interface HolidayCalendar {

    /**
     * Returns {@code true} if the given date is a public holiday in the given zone.
     *
     * <p>
     * Implementations may ignore {@code zone} if their holiday data is not
     * zone-specific (e.g. a UK bank holiday list applies to all UK timezones).
     *
     * @param date the date to test
     * @param zone the timezone context (may be used for regional holiday lookup)
     * @return {@code true} if {@code date} is a holiday
     */
    boolean isHoliday(LocalDate date, ZoneId zone);
}
