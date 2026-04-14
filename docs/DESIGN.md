# Quarkus Tarkus — Design Document

## Overview

Quarkus Tarkus provides human-scale WorkItem lifecycle management for the Quarkus
ecosystem. Any Quarkus application adds `quarkus-tarkus` as a dependency and gets a
human task inbox — WorkItems with expiry, delegation, escalation, priority, and audit
trail — usable standalone or via optional integrations with Quarkus-Flow, CaseHub,
and Qhorus.

Primary design specification: `docs/specs/2026-04-14-tarkus-design.md`

---

## Glossary

| Term | System | Meaning |
|---|---|---|
| `Task` | CNCF Serverless Workflow / Quarkus-Flow | Machine-executed workflow step — milliseconds, no assignee, no expiry |
| `Task` | CaseHub | CMMN case work unit — assigned to any worker (human or agent) via capabilities |
| `WorkItem` | Quarkus Tarkus | Human-resolved unit of work — minutes/days, has assignee, expiry, delegation, audit |

**Rule:** A `Task` is controlled by a machine. A `WorkItem` waits for a human.

---

## Component Structure

Maven multi-module layout following Quarkiverse conventions:

| Module | Artifact | Purpose |
|---|---|---|
| Parent | `quarkus-tarkus-parent` | BOM, version management |
| Runtime | `quarkus-tarkus` | Core — WorkItem model, service, REST API, lifecycle engine |
| Deployment | `quarkus-tarkus-deployment` | Build-time processor — feature registration, native config |
| *(future)* | `quarkus-tarkus-flow` | Quarkus-Flow `TaskExecutorFactory` SPI integration |
| *(future)* | `quarkus-tarkus-casehub` | CaseHub `WorkerRegistry` adapter |
| *(future)* | `quarkus-tarkus-qhorus` | Qhorus MCP tools |

---

## Technology Stack

| Concern | Choice | Notes |
|---|---|---|
| Runtime | Java 21 (on Java 26 JVM) | `maven.compiler.release=21` |
| Framework | Quarkus 3.32.2 | Inherits `quarkiverse-parent:21` |
| Persistence | Hibernate ORM + Panache (active record) | UUID PKs, `@PrePersist` timestamps |
| Schema migrations | Flyway | `V1__initial_schema.sql`; consuming app owns datasource config |
| Scheduler | `quarkus-scheduler` | Expiry cleanup job |
| JDBC (dev/test) | H2 (optional dep) | PostgreSQL for production |
| Native image | GraalVM 25 (target) | Validation planned after Phase 4 |

---

## Domain Model

### WorkItem (`runtime/model/`)

| Field | Type | Notes |
|---|---|---|
| `id` | UUID PK | Set in `@PrePersist` |
| `title` | String | Human-readable task name |
| `description` | String | What the human needs to do |
| `status` | WorkItemStatus enum | See lifecycle below |
| `priority` | WorkItemPriority enum | LOW, NORMAL, HIGH, CRITICAL |
| `assigneeId` | String | Tarkus worker ID |
| `requiredCapabilities` | String | Comma-separated for routing |
| `createdBy` | String | System or agent that created it |
| `delegationChain` | String | Comma-separated prior assignees |
| `payload` | TEXT | JSON context for the human |
| `resolution` | TEXT | JSON decision from the human |
| `expiresAt` | Instant | Deadline; null → use config default |
| `createdAt` / `updatedAt` | Instant | Managed by `@PrePersist` / `@PreUpdate` |

**WorkItemStatus lifecycle:**
```
PENDING → ASSIGNED → IN_PROGRESS → COMPLETED
                                  ↘ REJECTED
         ↘ DELEGATED (→ PENDING for new assignee)
PENDING / ASSIGNED / IN_PROGRESS → EXPIRED → ESCALATED
```

### AuditEntry (`runtime/model/`)
Append-only event log: `workItemId`, `event`, `actor`, `detail` (JSON), `occurredAt`.

---

## Services

| Service | Package | Responsibilities |
|---|---|---|
| `WorkItemService` | `runtime.service` | Create, assign, claim, complete, reject, delegate; enforces status transitions |
| `ExpiryCleanupJob` | `runtime.service` | `@Scheduled` — marks expired WorkItems, fires EscalationPolicy |
| `EscalationPolicy` | `runtime.service` | SPI — pluggable: notify, reassign, auto-reject |

---

## REST API Surface

`WorkItemResource` at `/tarkus/workitems`:

| Endpoint | Action |
|---|---|
| `POST /` | Create WorkItem |
| `GET /inbox?assignee=X&status=Y` | Human inbox query |
| `GET /{id}` | Full WorkItem + audit log |
| `PUT /{id}/claim` | Claim (PENDING → ASSIGNED) |
| `PUT /{id}/start` | Start work (ASSIGNED → IN_PROGRESS) |
| `PUT /{id}/complete` | Complete with resolution |
| `PUT /{id}/reject` | Reject with reason |
| `PUT /{id}/delegate?to=Y` | Delegate to another worker |

---

## Configuration

`TarkusConfig` — `@ConfigMapping(prefix = "quarkus.tarkus")`:

| Property | Default | Meaning |
|---|---|---|
| `quarkus.tarkus.default-expiry-hours` | 24 | Default WorkItem lifetime |
| `quarkus.tarkus.escalation-policy` | notify | `notify`, `reassign`, `auto-reject` |
| `quarkus.tarkus.cleanup.expiry-check-seconds` | 60 | Expiry job interval |

Consuming app owns all datasource config.

---

## Build Roadmap

| Phase | Status | What |
|---|---|---|
| **1 — Core data model** | ⬜ Pending | WorkItem + AuditEntry entities, Flyway V1, WorkItemService, TarkusConfig |
| **2 — REST API** | ⬜ Pending | WorkItemResource — all lifecycle endpoints |
| **3 — Lifecycle engine** | ⬜ Pending | ExpiryCleanupJob, EscalationPolicy SPI + defaults |
| **4 — CloudEvents** | ⬜ Pending | Event emission on all transitions |
| **5 — Quarkus-Flow integration** | ⬜ Pending | `quarkus-tarkus-flow`, TaskExecutorFactory SPI |
| **6 — CaseHub integration** | ⬜ Pending | `quarkus-tarkus-casehub`, WorkerRegistry adapter |
| **7 — Qhorus integration** | ⬜ Pending | `quarkus-tarkus-qhorus`, MCP tools |
| **8 — Native image** | ⬜ Pending | GraalVM native build validation |

---

## Testing Strategy

- `@QuarkusTest` + `@TestTransaction` per test method — each test rolls back, no data leakage
- H2 in-memory datasource for all tests; Flyway runs V1 migration at boot
- No mocks — all tests exercise real Panache against real H2
- Test TDD: write tests first, run to see RED, implement, run to see GREEN
