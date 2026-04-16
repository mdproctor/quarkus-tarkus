package io.quarkiverse.workitems.queues.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import io.quarkiverse.workitems.queues.model.FilterAction;
import io.quarkiverse.workitems.queues.model.FilterScope;
import io.quarkiverse.workitems.queues.model.WorkItemFilter;
import io.quarkiverse.workitems.queues.service.FilterConditionEvaluator;
import io.quarkiverse.workitems.queues.service.FilterEngine;
import io.quarkiverse.workitems.queues.service.FilterEvaluatorRegistry;
import io.quarkiverse.workitems.runtime.model.WorkItem;
import io.quarkiverse.workitems.runtime.model.WorkItemPriority;
import io.quarkiverse.workitems.runtime.model.WorkItemStatus;

@Path("/filters")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class FilterResource {

    @Inject
    FilterEvaluatorRegistry registry;

    @Inject
    FilterEngine filterEngine;

    public record CreateFilterRequest(String name, FilterScope scope, String ownerId,
            String conditionLanguage, String conditionExpression, List<FilterAction> actions) {
    }

    public record AdHocEvalRequest(String conditionLanguage, String conditionExpression,
            AdHocWorkItem workItem) {
    }

    public record AdHocWorkItem(String title, String status, String priority,
            String assigneeId, String category) {
    }

    @GET
    @Transactional
    public List<Map<String, Object>> list() {
        return WorkItemFilter.<WorkItemFilter> listAll().stream()
                .map(f -> Map.<String, Object> of(
                        "id", f.id, "name", f.name, "scope", f.scope,
                        "conditionLanguage", f.conditionLanguage, "active", f.active))
                .toList();
    }

    @POST
    @Transactional
    public Response create(final CreateFilterRequest req) {
        if ("lambda".equalsIgnoreCase(req.conditionLanguage())) {
            return Response.status(400)
                    .entity(Map.of("error", "Lambda filters are CDI beans — use jexl or jq."))
                    .build();
        }
        final WorkItemFilter f = new WorkItemFilter();
        f.name = req.name();
        f.scope = req.scope() != null ? req.scope() : FilterScope.ORG;
        f.ownerId = req.ownerId();
        f.conditionLanguage = req.conditionLanguage();
        f.conditionExpression = req.conditionExpression();
        f.actions = WorkItemFilter.serializeActions(req.actions() != null ? req.actions() : List.of());
        f.active = true;
        f.persist();
        return Response.status(201)
                .entity(Map.of("id", f.id, "name", f.name, "active", f.active))
                .build();
    }

    @PUT
    @Path("/{id}")
    @Transactional
    public Response update(@PathParam("id") final UUID id, final CreateFilterRequest req) {
        final WorkItemFilter f = WorkItemFilter.findById(id);
        if (f == null) {
            return Response.status(404).entity(Map.of("error", "Not found")).build();
        }
        if (req.name() != null) {
            f.name = req.name();
        }
        if (req.conditionExpression() != null) {
            f.conditionExpression = req.conditionExpression();
        }
        if (req.actions() != null) {
            f.actions = WorkItemFilter.serializeActions(req.actions());
        }
        return Response.ok(Map.of("id", f.id, "name", f.name)).build();
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    public Response delete(@PathParam("id") final UUID id) {
        final WorkItemFilter f = WorkItemFilter.findById(id);
        if (f == null) {
            return Response.status(404).entity(Map.of("error", "Not found")).build();
        }
        filterEngine.cascadeDelete(id);
        f.delete();
        return Response.noContent().build();
    }

    @POST
    @Path("/evaluate")
    public Response evaluate(final AdHocEvalRequest req) {
        final FilterConditionEvaluator evaluator = registry.find(req.conditionLanguage());
        if (evaluator == null) {
            return Response.status(400)
                    .entity(Map.of("error", "Unknown language: " + req.conditionLanguage())).build();
        }
        final WorkItem wi = new WorkItem();
        if (req.workItem() != null) {
            wi.title = req.workItem().title();
            wi.status = parseStatus(req.workItem().status());
            wi.priority = parsePriority(req.workItem().priority());
            wi.assigneeId = req.workItem().assigneeId();
            wi.category = req.workItem().category();
        }
        return Response.ok(Map.of("matches", evaluator.evaluate(wi, req.conditionExpression()))).build();
    }

    private WorkItemStatus parseStatus(final String s) {
        try {
            return s != null ? WorkItemStatus.valueOf(s) : WorkItemStatus.PENDING;
        } catch (IllegalArgumentException e) {
            return WorkItemStatus.PENDING;
        }
    }

    private WorkItemPriority parsePriority(final String p) {
        try {
            return p != null ? WorkItemPriority.valueOf(p) : WorkItemPriority.NORMAL;
        } catch (IllegalArgumentException e) {
            return WorkItemPriority.NORMAL;
        }
    }
}
