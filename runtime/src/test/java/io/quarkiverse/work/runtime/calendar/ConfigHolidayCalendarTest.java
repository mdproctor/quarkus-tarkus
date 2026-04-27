package io.quarkiverse.work.runtime.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.quarkiverse.work.runtime.config.WorkItemsConfig;

/**
 * Unit tests for ConfigHolidayCalendar — no Quarkus, no CDI.
 */
class ConfigHolidayCalendarTest {

    @Test
    void isHoliday_trueForConfiguredDate() {
        final ConfigHolidayCalendar cal = new ConfigHolidayCalendar(configWith("2026-12-25,2026-01-01"));
        assertThat(cal.isHoliday(LocalDate.of(2026, 12, 25), ZoneId.of("UTC"))).isTrue();
        assertThat(cal.isHoliday(LocalDate.of(2026, 1, 1), ZoneId.of("UTC"))).isTrue();
    }

    @Test
    void isHoliday_falseForNonConfiguredDate() {
        final ConfigHolidayCalendar cal = new ConfigHolidayCalendar(configWith("2026-12-25"));
        assertThat(cal.isHoliday(LocalDate.of(2026, 12, 26), ZoneId.of("UTC"))).isFalse();
    }

    @Test
    void isHoliday_falseWhenNoHolidaysConfigured() {
        final ConfigHolidayCalendar cal = new ConfigHolidayCalendar(configWith(""));
        assertThat(cal.isHoliday(LocalDate.of(2026, 12, 25), ZoneId.of("UTC"))).isFalse();
    }

    @Test
    void isHoliday_handlesWhitespaceInList() {
        final ConfigHolidayCalendar cal = new ConfigHolidayCalendar(configWith("2026-12-25 , 2026-01-01"));
        assertThat(cal.isHoliday(LocalDate.of(2026, 12, 25), ZoneId.of("UTC"))).isTrue();
        assertThat(cal.isHoliday(LocalDate.of(2026, 1, 1), ZoneId.of("UTC"))).isTrue();
    }

    @Test
    void isHoliday_zoneIgnored_dateMatchesRegardlessOfZone() {
        final ConfigHolidayCalendar cal = new ConfigHolidayCalendar(configWith("2026-12-25"));
        // Config-backed calendar is date-only — zone does not affect result
        assertThat(cal.isHoliday(LocalDate.of(2026, 12, 25), ZoneId.of("America/New_York"))).isTrue();
        assertThat(cal.isHoliday(LocalDate.of(2026, 12, 25), ZoneId.of("Asia/Tokyo"))).isTrue();
    }

    // ── stub ─────────────────────────────────────────────────────────────────

    private static WorkItemsConfig configWith(final String holidays) {
        return new WorkItemsConfig() {
            @Override
            public int defaultExpiryHours() {
                return 24;
            }

            @Override
            public int defaultClaimHours() {
                return 4;
            }

            @Override
            public String escalationPolicy() {
                return "notify";
            }

            @Override
            public String claimEscalationPolicy() {
                return "notify";
            }

            @Override
            public CleanupConfig cleanup() {
                return () -> 60;
            }

            @Override
            public RoutingConfig routing() {
                return () -> "least-loaded";
            }

            @Override
            public BusinessHoursConfig businessHours() {
                return new BusinessHoursConfig() {
                    @Override
                    public String timezone() {
                        return "UTC";
                    }

                    @Override
                    public String start() {
                        return "09:00";
                    }

                    @Override
                    public String end() {
                        return "17:00";
                    }

                    @Override
                    public String workDays() {
                        return "MON,TUE,WED,THU,FRI";
                    }

                    @Override
                    public Optional<String> holidays() {
                        return holidays.isBlank() ? Optional.empty() : Optional.of(holidays);
                    }

                    @Override
                    public Optional<String> holidayIcalUrl() {
                        return Optional.empty();
                    }
                };
            }
        };
    }
}
