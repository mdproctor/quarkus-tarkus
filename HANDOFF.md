# quarkus-work — Session Handover
**Date:** 2026-04-28

## Project Status

73 quarkus-work-reports tests (68 H2 + 5 PostgreSQL via Testcontainers — Flyway now runs on real PostgreSQL, no bypass). 605 runtime tests. All passing.
Epic #104 closed. Issues #142–145, #146 closed.

## What Was Done This Session

### Epic #104 — SLA Compliance Reporting — CLOSED

*Unchanged — `git show HEAD~5:HANDOFF.md`*

### Build Discipline Scripts

*Unchanged — `git show HEAD~5:HANDOFF.md`*

### Flyway Migration PostgreSQL Fixes — DONE

- `DOUBLE` → `DOUBLE PRECISION` in V13 (runtime), V1000/V1001/V1002 (ledger copies in this repo)
- Same fix applied to `quarkus-ledger` (quarkus-ledger#66 closed)
- `MODE=PostgreSQL` added to ALL H2 test JDBC URLs across every module in quarkus-work, quarkus-ledger, and casehub-engine — H2 now rejects non-standard types at test time
- `PostgresTestResource` Flyway bypass removed — PostgreSQL dialect tests run real migrations
- `casehub-engine` migrations: scanned, clean (no affected types)

### Cross-Module Conventions — casehub-parent

- `docs/conventions/` created with 4 RAG-friendly files (one rule per file):
  - `sql-type-portability.md` — DOUBLE PRECISION, SMALLINT, TIMESTAMP
  - `flyway-migration-rules.md` — namespace ranges, MODE=PostgreSQL
  - `optional-module-pattern.md` — Jandex library, zero cost
  - `quarkus-test-database.md` — H2 MODE=PostgreSQL, Testcontainers approach
- `docs/PLATFORM.md` updated with table linking to each convention

## Open / Next

| Priority | What |
|---|---|
| 1 | #106 Multi-instance tasks — design check needed (may be CaseHub concern) |
| 2 | #93 Distributed SSE — Redis pub/sub for WorkItemEventBroadcaster |

## Key References

- Build discipline rules: `CLAUDE.md` § Build Discipline
- PostgreSQL test approach: `CLAUDE.md` § Known Quarkiverse gotchas (PostgresDialectValidationTest entry)
- Platform conventions: `casehub-parent/docs/conventions/` (also linked from PLATFORM.md)
- Reports module spec: `docs/superpowers/specs/2026-04-27-sla-reporting-design.md`
- Blog: `blog/2026-04-28-mdp01-optional-reports-postgres-truth.md`
- Garden entries: GE-20260428-336f35, -0482d3, -5dbd37, -e75d4d, -fb8c51, -73d821
