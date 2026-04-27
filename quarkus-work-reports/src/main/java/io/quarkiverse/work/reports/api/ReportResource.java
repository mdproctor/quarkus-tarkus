package io.quarkiverse.work.reports.api;

import java.time.Instant;

import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import io.quarkiverse.work.reports.service.ActorReport;
import io.quarkiverse.work.reports.service.QueueHealthReport;
import io.quarkiverse.work.reports.service.ReportService;
import io.quarkiverse.work.reports.service.SlaBreachReport;
import io.quarkiverse.work.reports.service.ThroughputReport;
import io.quarkiverse.work.runtime.model.WorkItemPriority;

@Path("/workitems/reports")
@Produces(MediaType.APPLICATION_JSON)
public class ReportResource {

    @Inject
    ReportService reportService;

    @GET
    @Path("/sla-breaches")
    public SlaBreachReport slaBreaches(
            @QueryParam("from") final String from,
            @QueryParam("to") final String to,
            @QueryParam("category") final String category,
            @QueryParam("priority") final String priority) {
        return reportService.slaBreaches(
                parseInstant(from, "from"),
                parseInstant(to, "to"),
                category,
                parsePriority(priority));
    }

    @GET
    @Path("/actors/{actorId}")
    public ActorReport actorPerformance(
            @PathParam("actorId") final String actorId,
            @QueryParam("from") final String from,
            @QueryParam("to") final String to,
            @QueryParam("category") final String category) {
        return reportService.actorPerformance(
                actorId,
                parseInstant(from, "from"),
                parseInstant(to, "to"),
                category);
    }

    @GET
    @Path("/throughput")
    public ThroughputReport throughput(
            @QueryParam("from") final String from,
            @QueryParam("to") final String to,
            @QueryParam("groupBy") @DefaultValue("day") final String groupBy) {
        if (from == null || to == null) {
            throw new WebApplicationException(
                    "'from' and 'to' are required for throughput reports",
                    Response.Status.BAD_REQUEST);
        }
        if (!groupBy.equals("day") && !groupBy.equals("week") && !groupBy.equals("month")) {
            throw new WebApplicationException(
                    "Invalid groupBy '" + groupBy + "': must be day, week, or month",
                    Response.Status.BAD_REQUEST);
        }
        return reportService.throughput(parseInstant(from, "from"), parseInstant(to, "to"), groupBy);
    }

    @GET
    @Path("/queue-health")
    public QueueHealthReport queueHealth(
            @QueryParam("category") final String category,
            @QueryParam("priority") final String priority) {
        return reportService.queueHealth(category, parsePriority(priority));
    }

    private static WorkItemPriority parsePriority(final String priority) {
        if (priority == null || priority.isBlank()) {
            return null;
        }
        try {
            return WorkItemPriority.valueOf(priority.toUpperCase());
        } catch (final IllegalArgumentException e) {
            throw new WebApplicationException(
                    "Invalid priority: " + priority, Response.Status.BAD_REQUEST);
        }
    }

    private static Instant parseInstant(final String value, final String paramName) {
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (final java.time.format.DateTimeParseException e) {
            throw new WebApplicationException(
                    "Invalid ISO 8601 timestamp for '" + paramName + "': " + value,
                    Response.Status.BAD_REQUEST);
        }
    }
}
