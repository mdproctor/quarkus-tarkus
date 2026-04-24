# quarkus-work — Session Handover
**Date:** 2026-04-24

## Project Status

597 runtime tests, all green. Full build passes.

| Module | Tests |
|---|---|
| quarkus-work-api | 27 |
| quarkus-work-core | 53 |
| runtime | 597 |
| quarkus-work-ai | 77 |
| quarkus-work-examples | passing (3 pre-existing failures unrelated to spawn) |
| quarkus-work-ledger | 25 (LedgerIntegrationTest) |

## What Was Done This Session

### Rename: quarkus-workitems → quarkus-work
Complete rename — Maven groupId, artifactIds, Java packages, module directories, config prefix, feature name. Commit `098acfe`. Nothing published so no compatibility constraints.

### Architecture: layering and platform naming
- `docs/architecture/LAYERING.md` — canonical boundary rule between quarkus-work (primitives/events) and CaseHub (orchestration). Critical reading before any cross-project work.
- CaseHub confirmed as the platform/org name. casehub-engine is the CMMN core. quarkus-work will eventually be renamed casehub-work, quarkus-ledger → casehub-ledger. **Parked for a dedicated rename session.**
- Work/WorkItem/Task taxonomy documented in LAYERING.md.

### #133 Filter engine moved from quarkus-work-core → runtime
FilterRule (@Entity), FilterRuleResource, FilterRegistryEngine, etc. quarkus-work-core is now pure CDI + quarkus-work-api — no JPA, no REST. CaseHub can depend on it without a datasource.

### #105 Subprocess spawning — COMPLETE
All 8 tasks done, issues #127–#132 closed.

Key design: quarkus-work is the pure primitive layer. No orchestration.
- `callerRef` on WorkItem (V17) — opaque routing key, echoed in every lifecycle event. CaseHub embeds its planItemId.
- `WorkItemSpawnGroup` (V18) — idempotency tracking only, no completion state.
- `POST /workitems/{id}/spawn` + group endpoints
- `WorkEventType.SPAWNED` + ledger causedByEntryId wiring
- Cascade cancellation scoped via `createdBy = "system:spawn:{groupId}"`
- Full TDD: unit, integration, E2E, idempotency, cascade, correctness tests
- SpawnScenario example at `POST /examples/spawn/run`

### quarkus-ledger dependency version fix
quarkus-work-ledger now depends on `0.2-SNAPSHOT` (was stale `1.0.0-SNAPSHOT`). See CLAUDE.md gotcha.

## Open / Next

| Priority | What |
|---|---|
| 1 | Platform rename (quarkus-work → casehub-work, etc.) — parked, needs dedicated session |
| 2 | #101 Business-hours deadlines — `BusinessCalendar` SPI |
| 3 | #103 Notifications — `quarkus-work-notifications` module |
| 4 | #104 SLA compliance reporting |
| 5 | #105 child: `quarkus-work-casehub` adapter — routes `WorkItemLifecycleEvent` → CaseHub `CONTEXT_CHANGED` via callerRef |

## Key References

- Layering architecture: `docs/architecture/LAYERING.md`
- Spawn spec: `docs/superpowers/specs/2026-04-23-subprocess-spawning-design.md`
- casehub-engine path: `/Users/mdproctor/dev/casehub-engine` (not `~/claude/casehub` — that is the stale POC)
- quarkus-ledger: `~/claude/quarkus-ledger` — version is `0.2-SNAPSHOT`
