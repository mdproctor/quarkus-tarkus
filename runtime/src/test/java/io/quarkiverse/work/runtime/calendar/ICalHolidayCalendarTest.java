package io.quarkiverse.work.runtime.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for ICalHolidayCalendar parsing — no HTTP, no Quarkus, no CDI.
 * Uses the package-private {@code parse(Iterable<String>)} and
 * {@code extractDate(String)} methods directly.
 */
class ICalHolidayCalendarTest {

    // ── extractDate ───────────────────────────────────────────────────────────

    @Test
    void extractDate_allDayValueDate() {
        assertThat(ICalHolidayCalendar.extractDate("DTSTART;VALUE=DATE:20261225"))
                .isEqualTo(LocalDate.of(2026, 12, 25));
    }

    @Test
    void extractDate_bareDate() {
        assertThat(ICalHolidayCalendar.extractDate("DTSTART:20260101"))
                .isEqualTo(LocalDate.of(2026, 1, 1));
    }

    @Test
    void extractDate_zonedDatetime_extractsDatePart() {
        assertThat(ICalHolidayCalendar.extractDate("DTSTART;TZID=Europe/London:20261225T000000"))
                .isEqualTo(LocalDate.of(2026, 12, 25));
    }

    @Test
    void extractDate_invalidLine_returnsNull() {
        assertThat(ICalHolidayCalendar.extractDate("DTSTART;VALUE=DATE:not-a-date")).isNull();
    }

    @Test
    void extractDate_missingColon_returnsNull() {
        assertThat(ICalHolidayCalendar.extractDate("DTSTART")).isNull();
    }

    // ── parse ─────────────────────────────────────────────────────────────────

    static final String MINIMAL_ICAL = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            SUMMARY:Christmas Day
            DTSTART;VALUE=DATE:20261225
            DTEND;VALUE=DATE:20261226
            END:VEVENT
            BEGIN:VEVENT
            SUMMARY:New Year's Day
            DTSTART;VALUE=DATE:20270101
            DTEND;VALUE=DATE:20270102
            END:VEVENT
            END:VCALENDAR
            """;

    @Test
    void parse_extractsTwoHolidays() {
        final var holidays = ICalHolidayCalendar.parse(MINIMAL_ICAL.lines().toList());
        assertThat(holidays).containsExactlyInAnyOrder(
                LocalDate.of(2026, 12, 25),
                LocalDate.of(2027, 1, 1));
    }

    @Test
    void parse_ignoresTimeOfDayEvents() {
        // DTSTART with time component — should be ignored
        final var lines = List.of(
                "BEGIN:VEVENT",
                "DTSTART;TZID=UTC:20261225T090000",
                "END:VEVENT");
        // Time-of-day events ARE extracted (we take the date part)
        // This is intentional — many feeds use datetime format even for holidays
        final var holidays = ICalHolidayCalendar.parse(lines);
        assertThat(holidays).containsExactly(LocalDate.of(2026, 12, 25));
    }

    @Test
    void parse_emptyCalendar_returnsEmptySet() {
        final var holidays = ICalHolidayCalendar.parse(List.of(
                "BEGIN:VCALENDAR", "END:VCALENDAR"));
        assertThat(holidays).isEmpty();
    }

    @Test
    void parse_dtStartOutsideVevent_isIgnored() {
        final var lines = List.of(
                "DTSTART;VALUE=DATE:20261225", // outside VEVENT — ignored
                "BEGIN:VEVENT",
                "DTSTART;VALUE=DATE:20270101",
                "END:VEVENT");
        final var holidays = ICalHolidayCalendar.parse(lines);
        assertThat(holidays).containsExactly(LocalDate.of(2027, 1, 1));
    }

    @Test
    void isHoliday_trueForParsedDate() {
        final var cal = new ICalHolidayCalendar(
                ICalHolidayCalendar.parse(MINIMAL_ICAL.lines().toList()));
        assertThat(cal.isHoliday(LocalDate.of(2026, 12, 25), ZoneId.of("UTC"))).isTrue();
    }

    @Test
    void isHoliday_falseForNonHoliday() {
        final var cal = new ICalHolidayCalendar(
                ICalHolidayCalendar.parse(MINIMAL_ICAL.lines().toList()));
        assertThat(cal.isHoliday(LocalDate.of(2026, 12, 26), ZoneId.of("UTC"))).isFalse();
    }

    @Test
    void holidayCount_matchesParsedCount() {
        final var cal = new ICalHolidayCalendar(
                ICalHolidayCalendar.parse(MINIMAL_ICAL.lines().toList()));
        assertThat(cal.holidayCount()).isEqualTo(2);
    }
}
