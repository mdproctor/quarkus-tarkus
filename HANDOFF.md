# Quarkus WorkItems — Session Handover
**Date:** 2026-04-23

## Project Status

77+ AI module tests, 548 runtime tests, 14 examples tests. All green.

| Module | Tests |
|---|---|
| quarkus-work-api | 27 |
| quarkus-work-core | 53 |
| runtime | 548 |
| quarkus-work-ai | 77 |
| quarkus-work-examples | 14 |
| (others unchanged) | — |

## What Was Built This Session

### Epic #100 AI-Native Features — CLOSED

All remaining child issues closed:

| Issue | Feature | Commit |
|---|---|---|
| #119 | `CompositeSkillProfileProvider` — concatenates narratives from all active providers | `c11908b` |
| #120 | `SemanticWorkerSelectionStrategy` fallback to `LeastLoadedStrategy` on embedding failure | `c11908b` |
| #124 | `GET /workitems/{id}/resolution-suggestion` — LLM few-shot from past completions | `7d7321e` |
| #126 | Escalation summarisation — LLM briefing on EXPIRED/CLAIM_EXPIRED events | `58dc06f` |

### Epic #122 Documentation & Examples — CLOSED

All 9 example scenarios shipped + `docs/examples-guide.md` with why-not-just-what docs.

### ClaimSlaPolicy wired (#125) — `dd6bbf8`

Four pool-deadline strategies exist in quarkus-work-core (Continuation, Fresh, Single, Phase).
Now actually called: `WorkItemService.release/delegate` and `ClaimDeadlineJob` reset
`claimDeadline` via the active policy. V15 migration adds `accumulated_unclaimed_seconds`
and `last_returned_to_pool_at` to `work_item`.

### CaseHub alignment (linked: casehubio/engine#122)

ClaimSlaPolicy wiring done. FreshClockPolicy, SingleBudgetPolicy, PhaseClockPolicy marked
`@Alternative` — ContinuationPolicy is the sole default `@ApplicationScoped` bean.

### Ledger drift repair

quarkus-ledger switched from Panache to plain EntityManager — fixed throughout:
`JpaWorkItemLedgerEntryRepository`, `LedgerEventCapture`, `LedgerResource`.
Removed `@PersistenceUnit("qhorus")` from `TrustScoreJob` and `JpaLedgerEntryRepository`
in quarkus-ledger sibling (was Qhorus-specific, broke workitems context).

### Queues migration fix

`quarkus-work-queues` V2001 renamed to V2002 (conflict with ledger V2001).

## Closed This Session

Epics: #100, #98, #102, #122

Issues: #107, #108, #112–#116, #119, #120, #123–#126

## Open — Priority Order

| Priority | # | Epic | Status |
|---|---|---|---|
| 1 | #101 | Business-Hours Deadlines — `BusinessCalendar` SPI | not started |
| 2 | #103 | Notifications — webhooks on lifecycle events | not started |
| 3 | #104 | SLA Compliance Reporting — breach rate reports | not started |
| 4 | #105 | Subprocess Spawning — template-driven child WorkItems | not started |
| 5 | #106 | Multi-Instance Tasks — M-of-N parallel completion | not started |
| — | #92 | Distributed WorkItems / SSE (#93) | future |
| — | #79, #39 | External integrations, ProvenanceLink | blocked |
| — | #117 | RoundRobinStrategy | deferred |

## Deferred / Carry-Forward

- `quarkus-work-ledger` tests (`LedgerIntegrationTest` etc.) use old 15-arg
  `WorkItemCreateRequest` constructor — need updating (separate session)
- `ClaimSlaPolicy` — no config-driven policy selection yet; users choose via CDI `@Alternative @Priority`
- Embedding model for `EmbeddingSkillMatcher` — degrades to LeastLoaded fallback when not configured (correct behaviour now, thanks to #120)

## References

| What | Path |
|---|---|
| Design tracker | `docs/DESIGN.md` |
| Examples guide | `docs/examples-guide.md` |
| Integration guide (§8 quarkus-work, §9 semantic) | `docs/integration-guide.md` |
| API reference (/worker-skill-profiles, /resolution-suggestion, /escalation-summaries) | `docs/api-reference.md` |
| AI module | `quarkus-work-ai/src/main/java/io/quarkiverse/workitems/ai/` |
| ClaimSlaPolicy impls | `quarkus-work-core/src/main/java/io/quarkiverse/work/core/policy/` |
| Epic priority table | `CLAUDE.md` Work Tracking section |
