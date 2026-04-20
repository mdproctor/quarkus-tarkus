# Quarkus WorkItems — Session Handover
**Date:** 2026-04-20

## Project Status

400 runtime + 82 queues = 482 tests, 0 failures. All planned phases complete plus significant new feature work this session.

| Module | Tests |
|---|---|
| runtime | 400 |
| workitems-flow | 32 |
| quarkus-workitems-ledger | 75 |
| quarkus-workitems-queues | 82 |
| quarkus-workitems-queues-examples | 37 |
| quarkus-workitems-queues-dashboard | 20 |
| quarkus-workitems-flow-examples | 2 |
| quarkus-workitems-persistence-mongodb | 27 |
| quarkus-workitems-issue-tracker | 23 |
| testing | 16 |
| integration-tests | 19 (native) |

## What Was Built This Session

- **WorkItemLink** (#89) — structured URL references (design specs, policies, evidence, attachments). Pluggable `WorkItemLinkType` string constants. `POST/GET/DELETE /workitems/{id}/links?type=`
- **AsyncAPI spec** (#90) — `docs/asyncapi.yaml` (AsyncAPI 3.0) + `GET /q/asyncapi`. Documents `WorkItemLifecycleEvent` and `WorkItemQueueEvent` channels with full schemas and the tracker-not-live-snapshot invariant.
- **Micrometer metrics** (#91) — `workitems.active`, `workitems.by.status`, `workitems.overdue`, `workitems.claim.deadline.breached` gauges; `workitems.lifecycle.events{type}` counter; `workitems.queue.depth{queue}` in queues module.
- **Atomic claim** (#96) — `@Version Long version` on `WorkItem`; `OptimisticLockExceptionMapper` → 409 Conflict. `version` exposed in all WorkItem responses.
- **Distributed schedule execution** (#94) — `@Version` on `WorkItemSchedule` + `REQUIRES_NEW` per-schedule transactions. Prevents double-fire in clusters. No new infrastructure.
- **Epics #98–#106 created** — 9 epics for market leadership features (priority-ordered). Child issues #107–#110 created for the top two epics.

## Immediate Next Step

**Epic #98 — Form Schema, child issue #107**: `WorkItemFormSchema` entity (JSON Schema for payload + resolution), V11 migration, CRUD at `/workitem-form-schemas`. This is Priority 1 for enterprise adoption.

## Priority Roadmap

See CLAUDE.md Work Tracking table for full ordering. Top 3:
1. **#98** Form Schema — payload/resolution JSON Schema (child: #107 entity, #108 validation)
2. **#99** Audit History Query API — `GET /audit` cross-WorkItem search (child: #109 query, #110 SLA breaches)
3. **#100** AI-Native Features — confidence gating, semantic routing

## Open Issues

| Status | Issues |
|---|---|
| Active (priority 1–9) | #98–#106 (9 market leadership epics) |
| Distributed (some now) | #92 (Epic: #93 SSE, #94 ✅, #95 blocked, #96 ✅, #97 partial) |
| Blocked | #79 External Integrations, #39 ProvenanceLink |

## References

| What | Path |
|---|---|
| Design tracker | `docs/DESIGN.md` |
| Epic priority table | `CLAUDE.md` Work Tracking section |
| Competitor gap analysis | See this session's conversation |
| AsyncAPI spec | `docs/asyncapi.yaml` |
| WorkItems vs issue trackers | `docs/workitems-vs-issue-trackers.md` |
