package io.casehub.work.runtime.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Assertion helpers for business-hours deadline tests.
 *
 * <h2>Why this exists</h2>
 * <p>
 * Business-hours deadlines span weekends: 2 business hours requested at Friday 16:59 UTC
 * resolves to Monday 10:59 UTC — nearly 3 calendar days away. A naive
 * {@code isBefore(Instant.now().plus(1, DAYS))} fails every Friday evening.
 *
 * <h2>Usage contract</h2>
 * <ol>
 *   <li>Capture {@code before = Instant.now()} <em>before</em> any REST or service call
 *   <li>Pass {@code before} to these methods — never call {@code Instant.now()} inside assertions
 *   <li>The calendar bound is derived automatically from the requested business hours
 * </ol>
 *
 * <h2>Calendar bound formula</h2>
 * <p>
 * Worst case: business hours window starts just before close on Friday (16:59 UTC).
 * The deadline spans the full weekend and resolves on Monday. Bound:
 * {@code ceil(businessHours / 8.0)} work days {@code + 2} weekend days {@code + 1h} buffer.
 * For any count up to 40 business hours this stays within 8 calendar days.
 */
final class BusinessHoursAssert {

    private BusinessHoursAssert() {}

    /**
     * Asserts that {@code deadline} is in the future relative to {@code before},
     * and no further than the maximum calendar span for the given business hours count.
     *
     * @param deadline      the resolved deadline Instant
     * @param before        {@code Instant.now()} captured <em>before</em> the REST call
     * @param businessHours the number of business hours requested (used to compute the bound)
     */
    static void assertDeadlineInRange(final Instant deadline, final Instant before,
            final int businessHours) {
        assertThat(deadline)
                .as("deadline should be after the request was made")
                .isAfter(before);
        assertThat(deadline)
                .as("deadline should be within %d calendar days for %d business hours",
                        maxCalendarDays(businessHours), businessHours)
                .isBefore(maxBound(before, businessHours));
    }

    /**
     * Returns the maximum calendar-time upper bound for a business-hours deadline.
     * Accounts for worst-case weekend expansion (request on Friday just before close).
     */
    static Instant maxBound(final Instant before, final int businessHours) {
        return before.plus(maxCalendarDays(businessHours), ChronoUnit.DAYS)
                     .plus(1, ChronoUnit.HOURS);
    }

    /**
     * Maximum calendar days to allow for {@code businessHours} of work,
     * covering worst-case weekend expansion.
     */
    static int maxCalendarDays(final int businessHours) {
        // ceil(bh / 8) work days + 2 weekend days
        return (int) Math.ceil(businessHours / 8.0) + 2;
    }
}
