# quarkus-work — Session Handover
**Date:** 2026-04-29 (third session)

## Project Status

637 runtime tests passing. 76 quarkus-work-ledger tests passing (was 68 before this session).

## Earlier Sessions Today

*See `git show HEAD~2:HANDOFF.md` for #93 SPI design decision context.*
*See `git show HEAD~3:HANDOFF.md` for Epic #106 multi-instance WorkItems context.*

## What Was Done — This Session

### Three audit findings fixed in quarkus-work-ledger (Refs casehubio/quarkus-ledger#72)

**a) JSON injection in `buildDecisionContext`**
`LedgerEventCapture.buildDecisionContext()` used `String.format` to build JSON — quotes or backslashes in any field (actorId, assigneeId, etc.) silently produced malformed JSON. Replaced with Jackson `ObjectMapper` + `ObjectNode`. Jackson already on classpath via `quarkus-rest-jackson`.

**b) `eventSuffix()` null guard**
`EVENT_META` uses `Map.ofEntries()`. `Map.ofEntries().get(null)` throws NPE (not returns null like HashMap). Added guard in `onWorkItemEvent`: checks suffix is non-null and present in EVENT_META before building the entry; logs warning and returns otherwise. Also hardened `deriveCommandType/EventType/ActorRole` methods.

**c) 8 wrong test expectations in `TrustScoreComputerTest` + `TrustScoreJobTest`**
Tests expected `score ≈ 1.0` for actors with no attestations. Algorithm is Bayesian Beta(1,1) prior: no attestations → score = 0.5 (maximum uncertainty), not 1.0. Two recency tests also had `attestation.occurredAt` unset — decay uses attestation timestamp so all attestations got age=0 and identical weight. Fixed all 8 plus one in TrustScoreJobTest (same bug, different class).

### Garden entries submitted
- GE-20260429-177cbe — `Map.ofEntries().get(null)` throws NPE, not returns null
- GE-20260429-42fb02 — Bayesian Beta prior: no attestations → 0.5, not 1.0
- GE-20260429-f17b24 — Recency decay tests broken when `attestation.occurredAt` is null

## Open / Next

| Priority | What |
|---|---|
| 1 | #93 Distributed SSE — SPI design agreed; implementation deferred until concrete multi-node use case |
| — | Pick next from active epics table in CLAUDE.md |

## Key References

- Bug fix commit: `e44842e`
- Blog: `blog/2026-04-29-mdp03-ledger-audit-wrong-models.md`
- Previous handover (SSE SPI decision): `git show HEAD~2:HANDOFF.md`
- casehubio/quarkus-ledger#72 — audit issue tracking these fixes
