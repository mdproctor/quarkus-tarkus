# Quarkus WorkItems — Session Handover
**Date:** 2026-04-22

## Project Status

1019+ tests, 0 failures. Semantic skill matching implemented and verified.

| Module | Tests |
|---|---|
| quarkus-work-api | 15 |
| quarkus-work-core | 38 |
| runtime | 548 |
| workitems-flow | 32 |
| quarkus-workitems-ledger | 75 |
| quarkus-workitems-queues | 82 |
| quarkus-workitems-ai | 48 |
| quarkus-workitems-examples | 37 |
| quarkus-workitems-flow-examples | 2 |
| quarkus-workitems-queues-examples | 37 |
| quarkus-workitems-queues-dashboard | 20 |
| quarkus-workitems-persistence-mongodb | 27 |
| quarkus-workitems-issue-tracker | 23 |
| testing | 16 |
| integration-tests | 19 (native) |

## Branch State

Work lives on `feature/semantic-matching` (worktree at `.worktrees/semantic-matching`).
Issue #121 is closed. **Merge to `main` before starting next session.**

```bash
git checkout main
git merge feature/semantic-matching
```

## What Was Built This Session

**Epic #100 / Issue #121 — Semantic Skill Matching in `quarkus-workitems-ai`.**

### New SPIs in `quarkus-work-api`
- `SkillProfile` — record: `narrative` (String) + `attributes` (Map<String,String>)
- `SkillProfileProvider` — SPI: `getProfile(workerId, capabilities)` → `Optional<SkillProfile>`
- `SkillMatcher` — SPI: `score(SkillProfile, SelectionContext)` → double (0.0–1.0; -1.0 = skip)

### `SelectionContext` extended
- Added `title` (String) and `description` (String) — `WorkItemAssignmentService` now populates both when building context for strategy evaluation.

### `quarkus-workitems-ai` skill package (`ai/skill/`)
- **`WorkerSkillProfile`** — Panache entity, PK = `workerId` (String). Stores `skillNarrative` + `attributes` (JSON). Flyway V14 migration (`worker_skill_profile` table).
- **`WorkerSkillProfileResource`** — REST API at `/worker-skill-profiles` (CRUD: GET list, GET by id, POST, PUT, DELETE).
- **`WorkerProfileSkillProfileProvider`** — default (non-alternative) `SkillProfileProvider`. Reads `WorkerSkillProfile` from DB; returns empty if not found.
- **`CapabilitiesSkillProfileProvider`** — `@Alternative` provider. Builds a `SkillProfile` by joining the candidate's `requiredCapabilities` tags into a narrative sentence.
- **`ResolutionHistorySkillProfileProvider`** — `@Alternative` provider. Aggregates the worker's most-recent completed `AuditEntry` detail fields into a narrative. Returns empty if no history.
- **`EmbeddingSkillMatcher`** — implements `SkillMatcher`. Uses `dev.langchain4j:langchain4j-core` (plain library, not the Quarkus extension). `Instance<EmbeddingModel>` injection — unsatisfied → score -1.0. Cosine similarity of embedded skill narrative vs. embedded workItem title+description. Exception → score -1.0.
- **`SemanticWorkerSelectionStrategy`** — `@Alternative @Priority(1)` `WorkerSelectionStrategy`. Auto-activates when module is on classpath. Scores each candidate via `SkillMatcher`, filters out -1.0 scores (below threshold), sorts descending, returns top candidate. Falls back to `AssignmentDecision.noChange()` when no candidate clears the threshold.

### Test coverage (48 tests in quarkus-workitems-ai)
- `SemanticStrategyTest` (8), `EmbeddingSkillMatcherTest` (6), `CapabilitiesSkillProfileProviderTest` (5), `ResolutionHistorySkillProfileProviderTest` (5), `WorkerProfileSkillProfileProviderTest` (3), `WorkerSkillProfileTest` (3), `WorkerSkillProfileResourceTest` (7), `SemanticRoutingTest` (3), `LowConfidenceFilterTest` (8, pre-existing).

### Design spec
`docs/specs/2026-04-22-semantic-skill-matching-design.md`

## Immediate Next Step

1. Merge `feature/semantic-matching` → `main`
2. Continue **Epic #100** — next AI-Native feature: **AI-suggested resolution** (`GET /workitems/{id}/resolution-suggestion`) — LangChain4j AI service that reads WorkItem title, description, category, payload, and recent audit entries, then proposes a resolution JSON for the assigned worker.

## Priority Roadmap

| Priority | # | Epic | Status |
|---|---|---|---|
| 1 | #100 | AI-Native Features | **active** — next: AI-suggested resolution, escalation summarisation |
| 2 | #101 | Business-Hours Deadlines | **active** — BusinessCalendar SPI |
| 3 | #103 | Notifications | **active** — `quarkus-workitems-notifications` module |
| 4 | #104 | SLA Compliance Reporting | **active** — GET /workitems/reports/sla-breaches |
| 5 | #105 | Subprocess Spawning | **active** — WorkItemSpawnRule entity |
| 6 | #106 | Multi-Instance Tasks | **active** — MultiInstanceConfig on template |
| — | #92 | Distributed WorkItems | future — #93 (SSE) implementable now |
| — | #79 | External System Integrations | blocked — CaseHub/Qhorus not stable |
| — | #39 | ProvenanceLink (PROV-O) | blocked — awaiting #79 |
| ✅ | #121 | Semantic Skill Matching | complete |
| ✅ | #102 | Workload-Aware Routing | complete |
| ✅ | #98, #99 | Form Schema, Audit History | complete |
| ✅ | #77,78,80,81 | Collaboration, Queues, Storage, Platform | complete |

## Open Issues

| Status | Detail |
|---|---|
| #121 closed | Semantic skill matching done; branch needs merge |
| #119 open | CompositeSkillProfileProvider (chain multiple providers) — deferred |
| #120 open | FallbackWorkerSelectionStrategy (capability-filter fallback when no embedding model) — deferred |
| #117 deferred | RoundRobinStrategy (requires stateful cursor) |
| #79, #39 blocked | External integrations and provenance |

## References

| What | Path |
|---|---|
| Design tracker | `docs/DESIGN.md` |
| Primary design spec | `docs/specs/2026-04-14-tarkus-design.md` |
| Semantic matching spec | `docs/specs/2026-04-22-semantic-skill-matching-design.md` |
| Epic priority table | `CLAUDE.md` Work Tracking section |
| work-api SPIs | `quarkus-work-api/src/main/java/io/quarkiverse/work/api/` |
| AI skill package | `quarkus-workitems-ai/src/main/java/io/quarkiverse/workitems/ai/skill/` |
| V14 migration | `quarkus-workitems-ai/src/main/resources/db/migration/V14__worker_skill_profile.sql` |
