# CaseHub Work — Architecture

## Overview

CaseHub Work provides human-scale WorkItem lifecycle management for the Quarkus
ecosystem. Any Quarkus application adds `casehub-work` as a dependency and gets a
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
| `WorkItem` | CaseHub Work | Human-resolved unit of work — minutes/days, has assignee, expiry, delegation, audit |

**Rule:** A `Task` is controlled by a machine. A `WorkItem` waits for a human.

---

## Component Structure

Maven multi-module layout:

| Module | Artifact | Purpose |
|---|---|---|
| Parent | `casehub-work-parent` | BOM, version management |
| API | `casehub-work-api` | Pure Java SPI contracts — `WorkerCandidate`, `SelectionContext` (workItemId, title, description, category, requiredCapabilities, candidateUsers, candidateGroups), `AssignmentDecision`, `AssignmentTrigger`, `WorkerSelectionStrategy`, `WorkerRegistry`, `WorkEventType` (includes `SPAWNED`), `WorkLifecycleEvent`, `WorkloadProvider`, `EscalationPolicy`, `SkillProfile`, `SkillProfileProvider`, `SkillMatcher`. Spawn SPI: `SpawnPort`, `SpawnRequest`, `ChildSpec`, `SpawnResult`, `SpawnedChild`. groupId `io.casehub`. Zero runtime dependencies. CaseHub and other systems depend on this without pulling in the WorkItems stack. |
| Core | `casehub-work-core` | Generic work management implementations — `WorkBroker` (generic assignment orchestrator), `LeastLoadedStrategy`, `ClaimFirstStrategy`, `NoOpWorkerRegistry`, claim SLA policies. No JPA entities, no REST resources. CaseHub depends on this module directly. Jandex-indexed library, groupId `io.casehub`. |
| Runtime | `casehub-work` | Core — WorkItem model, storage SPI, JPA defaults, service, REST API, lifecycle engine, labels, vocabulary. Includes `WorkItemContextBuilder`, `JpaWorkloadProvider`, runtime actions (`ApplyLabelAction`, `OverrideCandidateGroupsAction`, `SetPriorityAction`), filter engine (`FilterAction` SPI, `FilterRegistryEngine`, `JexlConditionEvaluator`, `PermanentFilterRegistry`, `DynamicFilterRegistry`, `FilterRule`, `FilterRuleResource`). Subprocess spawning: `WorkItemSpawnService` (implements `SpawnPort`), `WorkItemSpawnGroup` entity, `WorkItemSpawnResource`, `SpawnGroupResource`. |
| Deployment | `casehub-work-deployment` | Build-time processor — feature registration, native config |
| Testing | `casehub-work-testing` | `InMemoryWorkItemStore` + `InMemoryAuditEntryStore` — no datasource needed for unit tests |
| Flow | `work-flow` | Quarkus-Flow integration — `HumanTaskFlowBridge`, `WorkItemFlowEventListener` |
| Ledger | `casehub-work-ledger` | Optional accountability module — command/event ledger, SHA-256 hash chain, peer attestation, EigenTrust reputation. Extends `io.casehub:casehub-ledger`. Zero core impact when absent. |
| Queues | `casehub-work-queues` | Optional label-based work queues — `WorkItemFilter` (JEXL/JQ/Lambda), `FilterChain`, `QueueView`, queue lifecycle events (`WorkItemQueueEvent` CDI events). Zero core impact when absent. |
| Examples | `casehub-work-examples` | Runnable scenario demos via `POST /examples/{name}/run` |
| Reports | `casehub-work-reports` | Optional SLA compliance reporting — `/workitems/reports/*`: sla-breaches, actors, throughput, queue-health. Caffeine cache, 5-min TTL. Zero core impact when absent. |
| Notifications | `casehub-work-notifications` | Optional outbound notification — HTTP webhook, Slack, Teams channels. `NotificationChannel` SPI in `casehub-work-api`. Flyway V3000. Zero core impact when absent. |
| AI | `casehub-work-ai` | Semantic skill matching: `SemanticWorkerSelectionStrategy` (`@Alternative @Priority(1)`), `EmbeddingSkillMatcher`, `WorkerSkillProfile` entity + REST API, `LowConfidenceFilterProducer`. Flyway V14, V4001. Zero core impact when absent. |
| PostgreSQL Broadcaster | `casehub-work-postgres-broadcaster` | Optional distributed SSE backend — `PostgresWorkItemEventBroadcaster` (`@Alternative @Priority(1)`) fans lifecycle events across cluster nodes via PostgreSQL LISTEN/NOTIFY (`casehub_work_events` channel). `WorkItemEventPayload` wire DTO. Fires AFTER_SUCCESS only. Zero extra infrastructure — reuses the datasource already required by the core extension. No Flyway migrations. |
| Queue PostgreSQL Broadcaster | `casehub-work-queues-postgres-broadcaster` | Optional distributed SSE backend for queue events — `PostgresWorkItemQueueEventBroadcaster` (`@Alternative @Priority(1)`) fans `WorkItemQueueEvent` CDI events across cluster nodes via PostgreSQL LISTEN/NOTIFY (`casehub_work_queue_events` channel). `WorkItemQueueEvent` is a plain record — no separate wire DTO needed. Fires AFTER_SUCCESS only. Depends on `casehub-work-queues` + `quarkus-reactive-pg-client`. No Flyway migrations. |
| Issue Tracker | `casehub-work-issue-tracker` | Link WorkItems to GitHub Issues, Jira, Linear. `IssueTrackerProvider` SPI. Flyway V5000. |
| Integration Tests | `integration-tests` | Black-box `@QuarkusIntegrationTest` suite and native image validation |
| MongoDB Persistence | `casehub-work-persistence-mongodb` | Optional MongoDB-backed `WorkItemStore` + `AuditEntryStore`. Drop-in replacement for JPA defaults — `candidateGroups`/`candidateUsers` stored as arrays; `WorkItemQuery` translated to MongoDB `Document` filters; `$regex` for label patterns. 27 tests via Dev Services. Zero core impact when absent. |
| *(future)* | `casehub-work-casehub` | CaseHub `WorkerRegistry` adapter (blocked: CaseHub not ready) |
| *(future)* | `casehub-work-qhorus` | Qhorus MCP tools (blocked: Qhorus not ready) |
| *(future)* | `casehub-work-persistence-redis` | Redis-backed `WorkItemStore` |

---

## Technology Stack

| Concern | Choice | Notes |
|---|---|---|
| Runtime | Java 21 (on Java 26 JVM) | `maven.compiler.release=21` |
| Framework | Quarkus 3.32.2 | Inherits `casehub-parent` BOM |
| Persistence | Hibernate ORM + Panache (active record) | UUID PKs, `@PrePersist` timestamps |
| Schema migrations | Flyway | `V1__initial_schema.sql`; consuming app owns datasource config |
| Scheduler | `quarkus-scheduler` | Expiry cleanup job |
| JDBC (dev/test) | H2 (optional dep) | PostgreSQL for production |
| Native image | GraalVM 25 | Validated: `@QuarkusIntegrationTest` suite, 0.084s startup |

---

## Domain Model

### WorkItem (`runtime/model/`)

| Field | Type | Notes |
|---|---|---|
| `id` | UUID PK | Set in `@PrePersist` |
| `title` | String | Human-readable task name |
| `description` | String | What the human needs to do |
| `category` | String | Classification: "finance", "legal", "security-review" |
| `formKey` | String | UI form reference — how frontends render this item |
| `status` | WorkItemStatus enum | See lifecycle below |
| `priority` | WorkItemPriority enum | LOW, NORMAL, HIGH, CRITICAL |
| `assigneeId` | String | Who currently has it (actual owner) |
| `owner` | String | Who is ultimately responsible; set on first delegation |
| `candidateGroups` | String | Comma-separated groups who can claim |
| `candidateUsers` | String | Comma-separated users individually invited to claim |
| `requiredCapabilities` | String | Comma-separated capability tags for routing |
| `createdBy` | String | System or agent that created it |
| `delegationState` | DelegationState enum | null \| PENDING \| RESOLVED |
| `delegationChain` | String | Comma-separated prior assignees (audit trail) |
| `payload` | TEXT | JSON context for the human |
| `resolution` | TEXT | JSON decision from the human |
| `claimDeadline` | Instant | Must be claimed by; null → use config default (0 = no deadline) |
| `expiresAt` | Instant | Must be completed by; null → use config default |
| `followUpDate` | Instant | Reminder date; surfaces in inbox, no escalation |
| `createdAt` / `updatedAt` | Instant | Managed by `@PrePersist` / `@PreUpdate` |
| `assignedAt` | Instant | When claimed/assigned |
| `startedAt` | Instant | When IN_PROGRESS began |
| `completedAt` | Instant | When terminal state reached |
| `suspendedAt` | Instant | When SUSPENDED |
| `priorStatus` | WorkItemStatus | Status before suspension; restored on resume |
| `labels` | `List<WorkItemLabel>` | 0..n labels; see Label Model below |
| `confidenceScore` | Double | Nullable. Set by AI agents (0.0–1.0). Null = no AI metadata. V13 migration. |
| `callerRef` | String | Nullable. Opaque routing key set at spawn time. Echoed in every `WorkItemLifecycleEvent`; never interpreted by casehub-work. V17 migration. |

**WorkItemSpawnGroup (`runtime/model/`)** — tracks a batch of children spawned together:

| Field | Type | Notes |
|---|---|---|
| `id` | UUID PK | Set in `@PrePersist` |
| `parentId` | UUID | The parent WorkItem that owns this group |
| `idempotencyKey` | String | Unique per parent — a second spawn call with the same `(parentId, idempotencyKey)` returns this group without creating new children |
| `createdAt` | Instant | When the group was created |

Completion tracking (`requiredCount`, `completedCount`, `groupStatus`) added for multi-instance groups (V21 migration).

**Business-hours SLA fields** — V19 migration adds columns to `work_item_template`:

| Field | Location | Notes |
|---|---|---|
| `expiresAtBusinessHours` | `WorkItemCreateRequest` | Resolved to absolute `expiresAt` via `BusinessCalendar` at create time |
| `claimDeadlineBusinessHours` | `WorkItemCreateRequest` | Resolved to absolute `claimDeadline` via `BusinessCalendar` |
| `defaultExpiryBusinessHours` | `WorkItemTemplate` | Passed through to spawned/instantiated WorkItems |
| `defaultClaimBusinessHours` | `WorkItemTemplate` | Passed through to spawned/instantiated WorkItems |

`BusinessCalendar` SPI (`casehub-work-api`): `addBusinessDuration(Instant, Duration, ZoneId)` and `isBusinessHour(Instant, ZoneId)`. Default implementation reads `casehub.work.business-hours.*` config. `HolidayCalendar` SPI: pluggable holiday data source. Optional iCal feed via `casehub.work.business-hours.holiday-ical-url`.

**WorkItemLabel (`runtime/model/`)** — each entry:

| Field | Type | Notes |
|---|---|---|
| `path` | String | `/`-separated label path, e.g. `legal/contracts/nda` |
| `persistence` | `LabelPersistence` | `MANUAL` (human-applied) or `INFERRED` (filter-applied, recomputed on every mutation) |
| `appliedBy` | String | userId (MANUAL) or filterId (INFERRED) — audit trail |

Vocabulary (`LabelVocabulary` + `LabelDefinition`) enforces path declarations at four scopes: `GLOBAL → ORG → TEAM → PERSONAL`.

**WorkItemStatus:**

| Status | Meaning |
|---|---|
| `PENDING` | Available for claiming; no assignee, or returned to pool |
| `ASSIGNED` | Claimed; not yet actively working |
| `IN_PROGRESS` | Being worked |
| `COMPLETED` | Successfully resolved |
| `REJECTED` | Human declined or declared uncomplete-able |
| `DELEGATED` | Transitional: ownership transferred, pending new assignment |
| `SUSPENDED` | On hold; will resume |
| `CANCELLED` | Externally cancelled by system or admin |
| `EXPIRED` | Passed completion deadline; triggers escalation policy |
| `ESCALATED` | Escalation policy has fired; terminal or awaiting admin action |

**Lifecycle transitions:**
```
PENDING → ASSIGNED (claim) | CANCELLED (admin)
ASSIGNED → IN_PROGRESS (start) | DELEGATED→PENDING | RELEASED→PENDING
         | SUSPENDED | CANCELLED (admin)
IN_PROGRESS → COMPLETED | REJECTED | DELEGATED→PENDING | SUSPENDED | CANCELLED (admin)
SUSPENDED → ASSIGNED | IN_PROGRESS (resume to prior state) | CANCELLED (admin)
PENDING | ASSIGNED | IN_PROGRESS | SUSPENDED → EXPIRED → ESCALATED
```

**DelegationState transitions:**
```
(null) → PENDING (delegate op) → RESOLVED (delegate completes) → (null) (owner confirms)
```

### WorkItemFormSchema (`runtime/model/`)

JSON Schema definitions for WorkItem payload and resolution, keyed optionally by category. No foreign-key relationship to WorkItem — independent lifecycle.

| Field | Type | Notes |
|---|---|---|
| `id` | UUID PK | Set in `@PrePersist` |
| `name` | String | Display name; required |
| `category` | String | Optional — null means global/catch-all |
| `payloadSchema` | TEXT | JSON Schema (draft-07) for `WorkItem.payload` |
| `resolutionSchema` | TEXT | JSON Schema for resolution submitted on complete |
| `schemaVersion` | String | Free-form (e.g. "1.0"); optional |
| `createdBy` | String | Required |
| `createdAt` | Instant | Set in `@PrePersist` |

### AuditEntry (`runtime/model/`)

Append-only event log: `workItemId`, `event`, `actor`, `detail` (JSON), `occurredAt`.

Audit event values: `CREATED` | `ASSIGNED` | `STARTED` | `COMPLETED` | `REJECTED` | `DELEGATED` | `RELEASED` | `SUSPENDED` | `RESUMED` | `CANCELLED` | `EXPIRED` | `ESCALATED`

---

## Storage SPI

Two interfaces in `runtime.repository` allow pluggable persistence:

| Interface | Default impl | Purpose |
|---|---|---|
| `WorkItemStore` | `JpaWorkItemStore` | `put(WorkItem)`, `get(UUID)`, `scan(WorkItemQuery)`, `scanAll()`. `WorkItemQuery` static factories: `inbox()`, `expired()`, `claimExpired()`, `byLabelPattern()`, `all()` |
| `AuditEntryStore` | `JpaAuditEntryStore` | `append`, `findByWorkItemId` |

`AuditQuery` (`runtime.repository`) — value object for cross-WorkItem audit searches:

| Field | Default | Notes |
|---|---|---|
| `actorId` | null | Exact match on actor |
| `from` / `to` | null | Inclusive date range on occurredAt |
| `event` | null | Exact match on event type |
| `category` | null | Filters via subquery on WorkItem.category |
| `page` / `size` | 0 / 20 | Offset pagination; size capped at 100 |

Default JPA implementations are `@ApplicationScoped`. Alternatives override via `@Alternative @Priority(1)`. The `casehub-work-testing` module provides `InMemoryWorkItemStore` + `InMemoryAuditEntryStore` for unit tests without a datasource.

---

## Services

| Service | Package | Responsibilities |
|---|---|---|
| `WorkItemService` | `runtime.service` | Create, assign, claim, complete, reject, delegate; enforces status transitions |
| `FormSchemaValidationService` | `runtime.service` | Pure JSON Schema draft-07 validator (networknt). Returns `List<String>` violations |
| `WorkItemAssignmentService` | `runtime.service` | Orchestrates worker selection on CREATED/RELEASED/DELEGATED via `WorkBroker` |
| `ClaimFirstStrategy` | `casehub-work-core` | Default no-op `WorkerSelectionStrategy` — pool stays open for claim-first |
| `LeastLoadedStrategy` | `casehub-work-core` | Pre-assigns to candidate with fewest active WorkItems |
| `NoOpWorkerRegistry` | `casehub-work-core` | Default `WorkerRegistry` — returns empty list (groups stay claim-first) |
| `JpaWorkloadProvider` | `runtime.service` | Counts active WorkItems per worker; implements `WorkloadProvider` SPI |
| `ExpiryCleanupJob` | `runtime.service` | `@Scheduled` — marks expired WorkItems, fires `EscalationPolicy` |
| `EscalationPolicy` | `casehub-work-api` | SPI — single `escalate(WorkLifecycleEvent)` method; pluggable: notify, reassign, auto-reject |

---

## Ledger Module

The optional `casehub-work-ledger` module records every WorkItem lifecycle transition as an
immutable `WorkItemLedgerEntry`. Activated by adding the module to the classpath — the
core extension is unchanged whether the module is present or not.

### Dependency on casehub-ledger

`casehub-work-ledger` depends on `io.casehub:casehub-ledger` — a domain-agnostic
shared library providing `LedgerEntry`, `LedgerAttestation`, `ActorTrustScore`,
`TrustScoreComputer`, `LedgerHashChain`, and their repositories. This allows CaseHub and
Qhorus to adopt the same ledger infrastructure without depending on WorkItems.

`WorkItemLedgerEntry` extends `LedgerEntry` via JPA JOINED inheritance.

### Configurable persistence unit

`casehub-ledger` 0.2-SNAPSHOT added a `@LedgerPersistenceUnit` CDI qualifier so the
ledger can use a named persistence unit instead of the CDI `@Default` EntityManager.
Needed when the consuming app has no default datasource — only a named one:

```properties
quarkus.ledger.datasource=mydb
```

Omitting the property retains the previous behaviour (CDI `@Default` EntityManager).
See casehub-ledger issue #46 (`1f8ca69`) for the implementation.

### actorType derivation

`LedgerEventCapture` derives `actorType` from the `actorId` prefix:

| Prefix | ActorType |
|---|---|
| `agent:` | `AGENT` |
| `system:` | `SYSTEM` |
| *(anything else)* | `HUMAN` |

### Integration point

`LedgerEventCapture` is an `@Observes WorkItemLifecycleEvent` CDI bean — the sole coupling
between the core and the ledger module. If the module is absent, events fire into the void.

---

## Event Broadcasting SPI

`WorkItemEventBroadcaster` (interface in `runtime.event`) fans out CDI lifecycle events to SSE subscribers. The default `LocalWorkItemEventBroadcaster` uses an in-process Mutiny `BroadcastProcessor`. Alternative backends override via `@Alternative @Priority(1)`.

**Concrete implementations:**

| Implementation | Module | Description |
|---|---|---|
| `LocalWorkItemEventBroadcaster` | `casehub-work` (runtime) | Default — in-process `BroadcastProcessor`. Only delivers to SSE clients on the same node. |
| `PostgresWorkItemEventBroadcaster` | `casehub-work-postgres-broadcaster` | Distributed — publishes via PostgreSQL NOTIFY (`casehub_work_events` channel) and re-broadcasts incoming LISTEN notifications to local SSE clients. All nodes receive all events. `@Alternative @Priority(1)` — auto-activates when the module is on the classpath. `WorkItemEventPayload` is the wire DTO (scalar fields only). Fires AFTER_SUCCESS: the CDI observer uses `TransactionPhase.AFTER_SUCCESS` so rolled-back events are never published. |

**Queue event broadcaster** — `WorkItemQueueEventBroadcaster` in `casehub-work-queues` follows the same SPI pattern:

| Implementation | Module | Description |
|---|---|---|
| `LocalWorkItemQueueEventBroadcaster` | `casehub-work-queues` | Default — in-process `BroadcastProcessor`. Only delivers to SSE clients on the same node. |
| `PostgresWorkItemQueueEventBroadcaster` | `casehub-work-queues-postgres-broadcaster` | Distributed — publishes via PostgreSQL NOTIFY (`casehub_work_queue_events` channel) and re-broadcasts incoming LISTEN notifications to local SSE clients. `@Alternative @Priority(1)` — auto-activates when the module is on the classpath. `WorkItemQueueEvent` is a plain record; no separate wire DTO is needed. Fires AFTER_SUCCESS. Implemented in #155. |
