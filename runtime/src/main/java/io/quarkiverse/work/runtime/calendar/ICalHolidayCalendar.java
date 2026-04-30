package io.casehub.work.runtime.calendar;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;

import io.casehub.work.api.HolidayCalendar;

/**
 * {@link HolidayCalendar} backed by an iCal ({@code .ics}) feed.
 *
 * <p>
 * Fetches the configured URL at startup and parses {@code VEVENT} blocks for
 * all-day events (identified by {@code DTSTART;VALUE=DATE:YYYYMMDD} lines).
 * Time-of-day events are ignored.
 *
 * <p>
 * This class is a plain Java implementation — it is instantiated by
 * {@link HolidayCalendarProducer} when
 * {@code casehub.work.business-hours.holiday-ical-url} is configured.
 * It is not a CDI bean itself.
 *
 * <p>
 * Example iCal line formats handled:
 *
 * <pre>
 * DTSTART;VALUE=DATE:20261225
 * DTSTART;TZID=Europe/London:20261225T000000
 * </pre>
 *
 * Only the date part ({@code YYYYMMDD}) is extracted; timezone and time-of-day are ignored.
 */
public class ICalHolidayCalendar implements HolidayCalendar {

    private static final DateTimeFormatter ICAL_DATE = DateTimeFormatter.BASIC_ISO_DATE; // yyyyMMdd

    private final Set<LocalDate> holidays;

    /**
     * Fetch and parse the iCal feed from {@code icalUrl} at construction time.
     *
     * @param icalUrl the URL of the {@code .ics} file; must be accessible at startup
     * @throws java.io.UncheckedIOException if the URL cannot be fetched or parsed
     */
    public ICalHolidayCalendar(final String icalUrl) {
        this.holidays = fetch(icalUrl);
    }

    /** Package-visible constructor for unit tests — supply pre-parsed content. */
    ICalHolidayCalendar(final Set<LocalDate> holidays) {
        this.holidays = Set.copyOf(holidays);
    }

    @Override
    public boolean isHoliday(final LocalDate date, final ZoneId zone) {
        return holidays.contains(date);
    }

    /** Returns the number of holidays loaded — useful for diagnostics. */
    public int holidayCount() {
        return holidays.size();
    }

    // ── parsing ──────────────────────────────────────────────────────────────

    private static Set<LocalDate> fetch(final String icalUrl) {
        try {
            final URL url = URI.create(icalUrl).toURL();
            try (final BufferedReader reader = new BufferedReader(
                    new InputStreamReader(url.openStream(), StandardCharsets.UTF_8))) {
                return parse(reader.lines().toList());
            }
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(
                    "Failed to fetch holiday iCal from: " + icalUrl, e);
        }
    }

    /**
     * Parse iCal content lines and extract all-day holiday dates.
     * Exposed package-private for unit testing without HTTP.
     */
    static Set<LocalDate> parse(final Iterable<String> lines) {
        final Set<LocalDate> result = new HashSet<>();
        boolean inVevent = false;

        for (final String line : lines) {
            final String trimmed = line.trim();

            if ("BEGIN:VEVENT".equalsIgnoreCase(trimmed)) {
                inVevent = true;
                continue;
            }
            if ("END:VEVENT".equalsIgnoreCase(trimmed)) {
                inVevent = false;
                continue;
            }

            if (inVevent && trimmed.toUpperCase().startsWith("DTSTART")) {
                final LocalDate date = extractDate(trimmed);
                if (date != null) {
                    result.add(date);
                }
            }
        }
        return Set.copyOf(result);
    }

    /**
     * Extract a {@link LocalDate} from a {@code DTSTART} iCal line.
     *
     * <p>
     * Handles:
     * <ul>
     * <li>{@code DTSTART;VALUE=DATE:20261225} — all-day date format</li>
     * <li>{@code DTSTART;TZID=Europe/London:20261225T000000} — zoned datetime (date part only)</li>
     * <li>{@code DTSTART:20261225} — bare date</li>
     * </ul>
     *
     * @return the parsed date, or {@code null} if the line cannot be parsed
     */
    static LocalDate extractDate(final String dtStartLine) {
        // Find the value after the last colon
        final int colonIdx = dtStartLine.lastIndexOf(':');
        if (colonIdx < 0) {
            return null;
        }
        String value = dtStartLine.substring(colonIdx + 1).trim();

        // Strip time component if present (e.g. T000000)
        final int tIdx = value.indexOf('T');
        if (tIdx >= 0) {
            value = value.substring(0, tIdx);
        }

        // Expect exactly 8 digits: YYYYMMDD
        if (value.length() != 8 || !value.chars().allMatch(Character::isDigit)) {
            return null;
        }

        try {
            return LocalDate.parse(value, ICAL_DATE);
        } catch (final Exception e) {
            return null;
        }
    }
}
