# SLA Compliance Reporting — Design Spec

**Date:** 2026-04-27
**Epic:** [#104](https://github.com/casehubio/quarkus-work/issues/104)
**Status:** approved

---

## Goal

Add four REST reporting endpoints to `quarkus-work` that give dashboards and ops tooling
business-level visibility into SLA compliance, actor performance, throughput trends, and
current queue health — without requiring a data warehouse or custom SQL.

---

## Scope

| Endpoint | Description | State |
|---|---|---|
| `GET /workitems/reports/sla-breaches` | WorkItems that missed their `expiresAt` deadline | scaffold exists |
| `GET /workitems/reports/actors/{actorId}` | Performance summary for one actor | scaffold exists |
| `GET /workitems/reports/throughput` | Created/completed counts over time, grouped by day/week/month | new |
| `GET /workitems/reports/queue-health` | Current overdue count, avg PENDING age, oldest unclaimed | new |

All four endpoints live in `ReportResource` (`/workitems/reports`).

---

## Definitions

### SLA Breach

A WorkItem breaches its SLA when:
- It is **still active** (`PENDING`, `ASSIGNED`, `IN_PROGRESS`, `SUSPENDED`, `ESCALATED`, `EXPIRED`) and `expiresAt` is in the past, **or**
- It reached a **terminal status** (`COMPLETED`, `REJECTED`, `CANCELLED`) and `completedAt > expiresAt`.

WorkItems with no `expiresAt` are excluded from SLA calculations — they carry no SLA commitment.

`completedAt == expiresAt` is **on-time** (breach is strictly `completedAt > expiresAt`).

### Actor Performance

Counts are derived from `AuditEntry` records where the actor matches.
`avgCompletionMinutes` is the average of `(completedAt − assignedAt)` across WorkItems
completed by this actor. Returns `null` when the actor has no completions in range.

### Throughput

- **Created count:** WorkItems whose `createdAt` falls in the bucket period
- **Completed count:** WorkItems in a terminal status whose `completedAt` falls in the bucket period
- These are independent per-bucket counts — a WorkItem created in week 1 and completed in week 3 contributes to both buckets separately

### Queue Health

Point-in-time snapshot at request time. Overdue = active status + `expiresAt` < now.

---

## Endpoint Contracts

### `GET /workitems/reports/sla-breaches`

Query params: `from` (ISO 8601, inclusive), `to` (ISO 8601, inclusive), `category`, `priority`

Response:
```json
{
  "items": [
    {
      "workItemId": "uuid",
      "category": "onboarding",
      "priority": "HIGH",
      "expiresAt": "2026-04-25T09:00:00Z",
      "completedAt": "2026-04-25T11:32:00Z",
      "status": "COMPLETED",
      "breachDurationMinutes": 152
    }
  ],
  "summary": {
    "totalBreached": 3,
    "avgBreachDurationMinutes": 94.5,
    "byCategory": { "onboarding": 2, "review": 1 }
  }
}
```

`breachDurationMinutes` is `max(0, completedAt − expiresAt)` for terminal items,
or `max(0, now − expiresAt)` for still-active items.

### `GET /workitems/reports/actors/{actorId}`

Query params: `from` (ISO 8601), `to` (ISO 8601), `category`

Response:
```json
{
  "actorId": "alice",
  "totalAssigned": 12,
  "totalCompleted": 9,
  "totalRejected": 1,
  "avgCompletionMinutes": 47.3,
  "byCategory": { "onboarding": 5, "review": 4 }
}
```

### `GET /workitems/reports/throughput`

Query params: `from` (ISO 8601, required), `to` (ISO 8601, required), `groupBy` (`day` | `week` | `month`, default `day`)

Response:
```json
{
  "from": "2026-04-01T00:00:00Z",
  "to": "2026-04-27T23:59:59Z",
  "groupBy": "week",
  "buckets": [
    { "period": "2026-W14", "created": 12, "completed": 8 },
    { "period": "2026-W15", "created": 18, "completed": 15 },
    { "period": "2026-W16", "created": 9,  "completed": 11 }
  ]
}
```

Period label format: `yyyy-MM-dd` (day), `yyyy-'W'ww` (ISO week), `yyyy-MM` (month).

### `GET /workitems/reports/queue-health`

Query params: `category` (optional), `priority` (optional)

Response:
```json
{
  "timestamp": "2026-04-27T17:00:00Z",
  "overdueCount": 5,
  "pendingCount": 23,
  "avgPendingAgeSeconds": 7200,
  "oldestUnclaimedCreatedAt": "2026-04-25T10:00:00Z",
  "criticalOverdueCount": 2
}
```

`oldestUnclaimedCreatedAt` is `null` when there are no PENDING items.

---

## Architecture

### Classes

```
runtime/src/main/java/io/quarkiverse/work/runtime/
└── api/
    ├── ReportResource.java          — all four endpoints; injects ReportService
    └── report/
        ├── ReportService.java       — all aggregate queries; @ApplicationScoped
        ├── SlaBreachReport.java     — response record (items + summary)
        ├── SlaBreachItem.java       — per-item breach record
        ├── SlaSummary.java          — totalBreached, avgBreachDurationMinutes, byCategory
        ├── ActorReport.java         — response record
        ├── ThroughputReport.java    — from, to, groupBy, buckets
        ├── ThroughputBucket.java    — period label + created + completed counts
        └── QueueHealthReport.java   — timestamp + overdue/pending counts + ages
```

`ReportResource` is a thin HTTP adapter — no business logic. `ReportService` owns all
queries and assembly. This boundary enables unit-testing the service without HTTP.

### Refactoring of existing scaffold

The current `ReportResource` has business logic inline and two bugs:

1. **N+1 in `computeByCategory`** — loads WorkItem entities in a loop via `em.find()`.
   Replace with a JPQL `GROUP BY w.category COUNT(w)` projection.
2. **Wrong epic references** — Javadoc cites `#99` and `#110`/`#111`. Update to `#104`
   and the correct child issue numbers once created.

The refactor moves all query logic out of `ReportResource` into `ReportService`.

---

## Query Strategy

### Throughput — HQL `date_trunc` + Java rollup

Query the DB at **day granularity** always:

```jpql
SELECT date_trunc('day', w.createdAt), COUNT(w)
FROM WorkItem w
WHERE w.createdAt >= :from AND w.createdAt <= :to
GROUP BY date_trunc('day', w.createdAt)
ORDER BY date_trunc('day', w.createdAt)
```

Hibernate 6 translates `date_trunc` per dialect — H2 2.x and PostgreSQL both supported
at `'day'` granularity. A second query does the same for `completedAt` on terminal items.

For `groupBy=week` and `groupBy=month`, the day buckets are merged in Java:
- **Week:** ISO week (`yyyy-'W'ww` via `IsoFields.WEEK_OF_WEEK_BASED_YEAR`)
- **Month:** calendar month (`yyyy-MM`)

This avoids dialect inconsistency in week semantics (PostgreSQL ISO week vs MySQL calendar week).

### Queue health — JPQL aggregates

Pure JPQL `COUNT` and aggregate queries; no entity loading:

```jpql
SELECT COUNT(w) FROM WorkItem w
WHERE w.status IN :activeStatuses AND w.expiresAt < :now

SELECT COUNT(w), MIN(w.createdAt) FROM WorkItem w
WHERE w.status = io.quarkiverse.work.runtime.model.WorkItemStatus.PENDING
```

### Actor byCategory — JPQL GROUP BY (replacing N+1)

```jpql
SELECT w.category, COUNT(w) FROM WorkItem w
WHERE w.assigneeId = :actorId
  AND w.status = io.quarkiverse.work.runtime.model.WorkItemStatus.COMPLETED
  AND w.completedAt >= :from AND w.completedAt <= :to
GROUP BY w.category
```

---

## Caching

All four endpoints are read-heavy and their results change slowly. Add `quarkus-cache`
dependency to `runtime/pom.xml` and annotate with `@CacheResult(cacheName = "reports")`.

Cache TTL: 5 minutes (configurable via `quarkus.cache.caffeine.reports.expire-after-write=5M`).

Cache is invalidated on application restart; no active invalidation needed (stale-by-up-to-5min
is acceptable for reporting).

### Query timeout

Set `jakarta.persistence.query.timeout=30000` (30s) on all report queries as a safety valve
against pathologically wide date ranges hitting unindexed data.

---

## Testing Strategy

### Unit tests — `ReportServiceTest` and bucket math

Test `ThroughputBucket` rollup logic in isolation (no DB, no HTTP):
- Day grouping: items on the same day land in the same bucket
- Week grouping: items Mon–Sun of the same ISO week merge correctly
- Month grouping: items across weeks in the same month merge correctly
- Boundary: item at midnight UTC on day boundary lands in correct bucket
- Empty input: returns empty bucket list

### Integration tests — `@QuarkusTest` (H2)

One test class per endpoint:

**`SlaBreachReportTest`** (scaffold exists, extend):
- Happy path: item completed after `expiresAt` appears in results
- Happy path: item completed before `expiresAt` excluded
- Open item past deadline included
- Boundary: `completedAt == expiresAt` → on-time, not breached
- Filter `from`/`to` by `expiresAt` window
- Filter `category` — only matching category returned
- Filter `priority` — only matching priority returned
- Summary: `totalBreached` matches `items.size()`
- Summary: `avgBreachDurationMinutes` > 0 when breaches exist
- Summary: `byCategory` groups correctly
- E2E: mixed compliance — 2 breaches + 1 on-time, verify both lists

**`ActorPerformanceReportTest`** (scaffold exists, extend):
- Zero counts for actor with no activity
- `totalAssigned` increments per claim
- `totalCompleted` increments per completion
- `totalRejected` increments per rejection
- `avgCompletionMinutes` is null when no completions
- `avgCompletionMinutes` ≥ 0 when completions exist
- `byCategory` counts per category
- `from`/`to` filter scopes counts correctly
- `category` filter scopes to that category only
- E2E: 2 completed + 1 rejected + 1 in-flight — verify all counts

**`ThroughputReportTest`** (new):
- Happy path: items created/completed in range appear in correct day bucket
- Items outside range excluded
- `groupBy=day`: one bucket per day with activity
- `groupBy=week`: items spanning same ISO week in one bucket
- `groupBy=month`: items spanning same month in one bucket
- No items in range: empty buckets list
- Created and completed counts are independent
- In-flight item (no `completedAt`): appears in created count only
- Boundary: item at exactly `from`/`to` boundary is included
- Missing `groupBy`: defaults to `day`
- Response includes `from`, `to`, `groupBy` echoed back

**`QueueHealthReportTest`** (new):
- `pendingCount` reflects current PENDING items
- `overdueCount` reflects active items past `expiresAt`
- `criticalOverdueCount` is a subset of `overdueCount`
- `avgPendingAgeSeconds` ≥ 0
- `oldestUnclaimedCreatedAt` is null when no PENDING items
- `oldestUnclaimedCreatedAt` is not null when PENDING items exist
- Completed items excluded from all counts
- `category` filter scopes all counts
- `priority` filter scopes all counts
- E2E: create mix of PENDING, overdue CRITICAL, completed — verify all fields

### Correctness tests (within integration suite)

- SLA: items with no `expiresAt` never appear in breach report
- Throughput: `created` + `completed` counts can differ (in-flight items)
- Actor: `avgCompletionMinutes` is `assignedAt → completedAt`, not `createdAt → completedAt`
- Queue health: `overdueCount` only counts active statuses, not COMPLETED/CANCELLED

### Robustness tests

- SLA breach: empty result set (no breaches in range) → 200 with empty `items`, zero summary
- Actor: unknown actor → 200 with all zero counts, no 404
- Throughput: `from` == `to` (single instant) → 200 with zero or one bucket
- Queue health: all items completed → all zero counts, null `oldestUnclaimedCreatedAt`
- Invalid `priority` enum value → 400
- Invalid ISO 8601 date → 400
- Extremely wide date range (year 2000 to 2099) → 200 within timeout

### E2E / @QuarkusIntegrationTest

Add `WorkItemReportNativeIT` to `integration-tests/` module:
- Smoke each of the four endpoints: 200, correct JSON structure
- One SLA breach scenario end-to-end

### Testcontainers PostgreSQL dialect validation

Add a `PostgresTestProfile` to `integration-tests/` using `quarkus-jdbc-postgresql` +
Testcontainers. Run the throughput report `groupBy=day`, `groupBy=week`, `groupBy=month`
against a real PostgreSQL instance to catch any HQL dialect divergence that H2 masks.

---

## Documentation Updates

- **`docs/DESIGN.md`** — add reporting section: endpoints, query strategy, caching approach
- **`CLAUDE.md` project structure** — add `api/report/` subpackage with its classes
- **Javadoc** — correct all `@see` references from `#99` → `#104` and child issue numbers
- **`docs/api-reference.md`** — add report endpoints with request/response examples

---

## Issues to Create Under Epic #104

| # | Title |
|---|---|
| A | Refactor ReportResource: extract ReportService, fix N+1, correct epic refs |
| B | Add throughput time-series endpoint with full TDD |
| C | Add queue-health endpoint with full TDD |
| D | Add E2E and Testcontainers PostgreSQL dialect tests for report endpoints |
