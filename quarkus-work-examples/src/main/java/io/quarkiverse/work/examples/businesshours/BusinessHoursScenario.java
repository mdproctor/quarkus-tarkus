package io.quarkiverse.work.examples.businesshours;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import io.quarkiverse.work.api.BusinessCalendar;
import io.quarkiverse.work.examples.StepLog;
import io.quarkiverse.work.runtime.model.WorkItem;
import io.quarkiverse.work.runtime.model.WorkItemCreateRequest;
import io.quarkiverse.work.runtime.model.WorkItemPriority;
import io.quarkiverse.work.runtime.service.WorkItemService;

/**
 * Business-hours SLA example.
 *
 * <p>
 * Demonstrates that WorkItem deadlines are set in business hours, not wall-clock hours.
 * A loan-approval WorkItem with a 48-business-hour SLA is created; the resulting
 * {@code expiresAt} is shown alongside the equivalent wall-clock calculation to
 * make the difference visible.
 *
 * <p>
 * Also shows the {@code BusinessCalendar} SPI directly for deadline preview.
 *
 * <pre>
 *   POST /examples/business-hours/run
 *   GET  /examples/business-hours/preview?hours=48&zone=Europe/London
 * </pre>
 */
@Path("/examples/business-hours")
@Produces(MediaType.APPLICATION_JSON)
public class BusinessHoursScenario {

    @Inject
    WorkItemService workItemService;

    @Inject
    BusinessCalendar businessCalendar;

    /**
     * Run the scenario: create a loan-approval WorkItem with a 48-business-hour SLA
     * and show how the deadline differs from a simple 48h wall-clock calculation.
     */
    @POST
    @Path("/run")
    @Transactional
    public Response run() {
        final List<StepLog> steps = new ArrayList<>();
        final ZoneId zone = ZoneId.of("UTC");

        // Step 1: create with 48 business-hour SLA
        final WorkItem wi = workItemService.create(new WorkItemCreateRequest(
                "Loan application review — 48 business-hour SLA",
                "Review and approve or reject the loan application within 48 business hours.",
                "loan-approval",
                null, WorkItemPriority.HIGH,
                null, "loan-officers", null, null,
                "finance-system", null,
                null, null, null, null, null, null,
                null, 48 // expiresAtBusinessHours = 48
        ));
        steps.add(new StepLog(1,
                "Created loan-approval WorkItem with expiresAtBusinessHours=48",
                wi.id));

        // Step 2: compare with wall-clock
        final java.time.Instant wallClock48h = java.time.Instant.now()
                .plus(Duration.ofHours(48));
        final long calendarHoursToDeadline = Duration.between(
                java.time.Instant.now(), wi.expiresAt).toHours();

        steps.add(new StepLog(2,
                "expiresAt resolves to " + calendarHoursToDeadline
                        + " wall-clock hours from now (always ≥ 48, more if weekend/nights crossed)",
                wi.id));

        return Response.ok(Map.of(
                "scenario", "business-hours-sla",
                "steps", steps,
                "workItemId", wi.id.toString(),
                "expiresAt", wi.expiresAt.toString(),
                "wallClock48hEquivalent", wallClock48h.toString(),
                "calendarHoursToDeadline", calendarHoursToDeadline,
                "note", "48 business hours always spans more calendar time — nights, weekends, and holidays are skipped"))
                .build();
    }

    /**
     * Preview what deadline a given number of business hours resolves to from now.
     *
     * <p>
     * Useful for operators verifying that the calendar is configured correctly
     * before deploying to production.
     *
     * @param hours number of business hours (default 48)
     * @param zone IANA timezone (default UTC)
     */
    @GET
    @Path("/preview")
    public Response preview(
            @jakarta.ws.rs.QueryParam("hours") @jakarta.ws.rs.DefaultValue("48") int hours,
            @jakarta.ws.rs.QueryParam("zone") @jakarta.ws.rs.DefaultValue("UTC") String zone) {

        final ZoneId zoneId = ZoneId.of(zone);
        final java.time.Instant now = java.time.Instant.now();
        final java.time.Instant deadline = businessCalendar.addBusinessDuration(
                now, Duration.ofHours(hours), zoneId);

        return Response.ok(Map.of(
                "requestedBusinessHours", hours,
                "zone", zone,
                "now", now.toString(),
                "nowLocal", ZonedDateTime.now(zoneId).toString(),
                "deadline", deadline.toString(),
                "deadlineLocal", deadline.atZone(zoneId).toString(),
                "calendarHoursSpanned", Duration.between(now, deadline).toHours(),
                "isBusinessHourNow", businessCalendar.isBusinessHour(now, zoneId))).build();
    }
}
