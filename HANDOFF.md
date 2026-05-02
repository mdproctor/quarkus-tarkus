# casehub-work — Session Handover
**Date:** 2026-05-02

## Project Status

CI green. Build passing on GitHub. Working tree clean.

## What Was Done This Session

*Earlier work (distributed SSE, GitHub sync, refinement epic) — `git show HEAD~4:HANDOFF.md`*

### Post-wrap CI fixes (commits c9ea3e0, 1b59d9a, 6b309d3)

**BusinessHoursIntegrationTest** (0f62b90, c9ea3e0):
- Root cause: May 1 is a Friday — 2 business hours from Friday 19:35 UTC resolves to Monday 11:00 (~3 calendar days). Test asserted `isBefore(Instant.now() + 1 day)` — wrong bound, also called after REST call.
- Fix: capture `before` before REST call; use `4 DAYS` bound. Then extracted `BusinessHoursAssert.assertDeadlineInRange(deadline, before, businessHours)` helper with formula `ceil(bh/8) + 2` calendar days. Issue #158 created and closed.

**WorkItemGroupLifecycleEventTest.completedEventFiresExactlyOnceAtThreshold** (1b59d9a):
- Root cause: `onThresholdReached` defaults to CANCEL in `MultiInstanceSpawnService`. When child[1] hits the threshold, coordinator cancels child[2]. Test then tried to complete child[2] — OCC race (cancel bumped version, test held stale reference).
- Fix: complete only `requiredCount` (2) children, not all 3. The surplus child is both unnecessary and unsafe to complete after threshold is met.

**JpaWorkItemLedgerEntryRepository** (6b309d3):
- Root cause: `casehub-ledger:0.2-SNAPSHOT` added three new abstract methods to `LedgerEntryRepository`. CI pulls latest SNAPSHOT from GitHub Packages; local build used older cached jar.
- Fix: implemented all three using existing named queries on `LedgerAttestation`.

### Also this session (Qhorus doc, not in casehub-work repo)

Wrote `qhorus/docs/work-and-workitems.md` — normative layer framing of Work vs WorkItem. Core thesis: Qhorus core is complete for machines; casehub-work IS the human-agent layer (not gap-filling). Key content: speech act mapping, principled boundary (SUSPENDED, sub-delegation), extension contract, cross-channel correlation, Layer 2 example. Discussion also surfaced SUSPEND/DELEGATE as extension candidates and the two-layer design philosophy.

## Open / Next

| Priority | What |
|---|---|
| 1 | #97 — wait for qhorus#131 + qhorus#132, then build `casehub-work-qhorus` |
| 2 | #156 phase 2 — incoming GitHub webhooks |
| 3 | #117 — RoundRobinStrategy (small) |
| — | #152 — examples split (parked) |

## Key References

- CI: green as of `6b309d3`
- BusinessHoursAssert: `runtime/src/test/java/io/casehub/work/runtime/calendar/BusinessHoursAssert.java`
- Normative doc: `~/claude/casehub/qhorus/docs/work-and-workitems.md`
- Previous full handover: `git show HEAD~4:HANDOFF.md`
