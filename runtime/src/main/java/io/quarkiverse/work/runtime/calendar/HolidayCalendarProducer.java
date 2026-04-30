package io.casehub.work.runtime.calendar;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import io.casehub.work.api.HolidayCalendar;
import io.casehub.work.runtime.config.WorkItemsConfig;
import io.quarkus.arc.DefaultBean;

/**
 * CDI producer for the default {@link HolidayCalendar} implementation.
 *
 * <h2>Selection logic</h2>
 * <ol>
 * <li>If {@code casehub.work.business-hours.holiday-ical-url} is set,
 * an {@link ICalHolidayCalendar} is produced — it fetches the iCal feed
 * at startup and caches all holiday dates.</li>
 * <li>Otherwise a {@link ConfigHolidayCalendar} is produced — it reads the
 * static date list from {@code casehub.work.business-hours.holidays}.</li>
 * </ol>
 *
 * <h2>Overriding</h2>
 * <p>
 * Because the produced bean is annotated {@link DefaultBean}, any application-provided
 * {@code @ApplicationScoped} CDI bean that implements {@link HolidayCalendar} takes
 * precedence automatically — no {@code @Alternative} or priority wiring needed.
 *
 * <pre>
 * {@literal @}ApplicationScoped
 * public class CompanyHolidayCalendar implements HolidayCalendar {
 *     {@literal @}Override
 *     public boolean isHoliday(LocalDate date, ZoneId zone) {
 *         // query your HR system, database, or hard-coded list
 *         return ...;
 *     }
 * }
 * </pre>
 */
@ApplicationScoped
public class HolidayCalendarProducer {

    @Inject
    WorkItemsConfig config;

    /**
     * Produce the active {@link HolidayCalendar}.
     *
     * <p>
     * Uses {@link DefaultBean} so any user-supplied {@link HolidayCalendar} bean wins
     * without requiring {@code @Alternative} or priority annotations.
     *
     * @return the iCal-backed calendar when a URL is configured, otherwise the
     *         static-config-backed calendar
     */
    @Produces
    @DefaultBean
    @ApplicationScoped
    public HolidayCalendar produce() {
        return config.businessHours().holidayIcalUrl()
                .filter(url -> !url.isBlank())
                .map(ICalHolidayCalendar::new)
                .map(HolidayCalendar.class::cast)
                .orElseGet(() -> new ConfigHolidayCalendar(config));
    }
}
