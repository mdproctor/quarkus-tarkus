package io.casehub.work.runtime.api;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import io.casehub.work.runtime.model.AuditEntry;
import io.casehub.work.runtime.repository.AuditEntryStore;
import io.casehub.work.runtime.repository.AuditQuery;

/**
 * Cross-WorkItem audit history query API.
 *
 * <pre>
 * GET /audit                                  — list all (paginated)
 * GET /audit?actorId=alice                    — filter by actor
 * GET /audit?event=COMPLETED                  — filter by event type
 * GET /audit?from=ISO8601&to=ISO8601          — filter by date range
 * GET /audit?category=finance                 — filter to WorkItems in that category
 * GET /audit?page=0&size=20                   — pagination (size capped at 100)
 * </pre>
 *
 * <p>
 * All filter parameters are optional and combinable. The response envelope includes
 * the matched page, the current page/size, and the total matching count (for client
 * pagination controls).
 *
 * @see <a href="https://github.com/mdproctor/quarkus-work/issues/109">Issue #109</a>
 * @see <a href="https://github.com/mdproctor/quarkus-work/issues/99">Epic #99</a>
 */
@Path("/audit")
@Produces(MediaType.APPLICATION_JSON)
public class AuditResource {

    @Inject
    AuditEntryStore auditStore;

    /**
     * Query audit history across all WorkItems.
     *
     * @param actorId exact match on actor field
     * @param from inclusive lower bound on occurredAt (ISO 8601)
     * @param to inclusive upper bound on occurredAt (ISO 8601)
     * @param event exact match on event type (e.g. "COMPLETED")
     * @param category filter to WorkItems whose category matches
     * @param page zero-based page number (default 0)
     * @param size page size, capped at 100 (default 20)
     * @return paginated audit entry envelope
     */
    @GET
    public Map<String, Object> queryAudit(
            @QueryParam("actorId") final String actorId,
            @QueryParam("from") final String from,
            @QueryParam("to") final String to,
            @QueryParam("event") final String event,
            @QueryParam("category") final String category,
            @QueryParam("page") @DefaultValue("0") final int page,
            @QueryParam("size") @DefaultValue("20") final int size) {

        final AuditQuery query = AuditQuery.builder()
                .actorId(actorId)
                .from(from != null ? Instant.parse(from) : null)
                .to(to != null ? Instant.parse(to) : null)
                .event(event)
                .category(category)
                .page(page)
                .size(size)
                .build();

        final List<AuditEntry> entries = auditStore.query(query);
        final long total = auditStore.count(query);

        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("entries", entries.stream().map(this::toResponse).toList());
        result.put("page", query.page());
        result.put("size", query.size());
        result.put("total", total);
        return result;
    }

    private Map<String, Object> toResponse(final AuditEntry e) {
        final Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.id);
        m.put("workItemId", e.workItemId);
        m.put("event", e.event);
        m.put("actor", e.actor);
        m.put("detail", e.detail);
        m.put("occurredAt", e.occurredAt);
        return m;
    }
}
