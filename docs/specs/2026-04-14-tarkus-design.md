# Quarkus Tarkus — Design Specification v1.0

> *Human-scale WorkItem lifecycle management for the Quarkus Native AI Agent Ecosystem.*

---

## Glossary

Three systems in this ecosystem all use the word "task" to mean different things. This glossary is authoritative.

**Task** *(io.serverlessworkflow.api.types.Task — CNCF Serverless Workflow / Quarkus-Flow)*
A step within a workflow definition executed under machine control. Has lifecycle:
started → completed / failed / suspended / resumed. Executes in milliseconds to seconds.
No assignee, no delegation, no expiry. The engine decides when and how it runs.
Examples: call an HTTP endpoint, emit a CloudEvent, evaluate a JQ expression.

**Task** *(io.casehub.worker.Task — CaseHub)*
A unit of work within a CaseHub case, following CMMN terminology. Assigned to a worker
(human or agent) via capability matching. Has lifecycle: PENDING → ASSIGNED → RUNNING →
WAITING → COMPLETED / FAULTED / CANCELLED. The task model is unified — human workers
and agent workers are the same concept in CaseHub. When a CaseHub Task is routed to a
human worker, the `quarkus-tarkus-casehub` adapter creates a corresponding Tarkus WorkItem.

**WorkItem** *(io.quarkiverse.tarkus.runtime.model.WorkItem — Quarkus Tarkus)*
A unit of work requiring human attention or judgment. Has lifecycle:
PENDING → ASSIGNED → IN_PROGRESS → COMPLETED / REJECTED / DELEGATED / ESCALATED / EXPIRED.
Persists minutes to days. Has assignee, priority, deadline, delegation chain, audit trail.
Any system creates one — Quarkus-Flow, CaseHub, Qhorus, or a plain REST call.
A human resolves it.

**The one-sentence rule:** A `Task` is controlled by a machine. A `WorkItem` waits for a human.

---

## What Tarkus Is

Quarkus Tarkus is a standalone Quarkiverse extension providing a **human task inbox** — a place for human workers to see what needs their attention, act on it, delegate it, and have it automatically escalate when it expires.

It is **not** a workflow engine, a case manager, or an agent communication mesh. It is the layer that sits between those systems and the human who needs to make decisions.

Any Quarkus application can embed Tarkus to get:
- A `WorkItem` entity with full lifecycle management
- A REST inbox API that any UI (Claudony dashboard, custom frontend) can consume
- Expiry detection and pluggable escalation policies
- Delegation chains with full audit trail
- CloudEvent emission for integration with external systems

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│  Quarkus Tarkus (standalone)                                    │
│                                                                  │
│  REST API /tarkus/workitems  ←── Claudony dashboard             │
│         │                        (or any UI)                    │
│         ▼                                                        │
│  WorkItemService                                                 │
│  ├── create / assign / claim / complete / reject / delegate     │
│  ├── ExpiryCleanupJob (@Scheduled)                               │
│  └── EscalationPolicy SPI                                       │
│         │                                                        │
│         ▼                                                        │
│  WorkItem (Panache entity)   AuditEntry (Panache entity)        │
│  H2 (dev/test) · PostgreSQL (production)                        │
└─────────────────────────────────────────────────────────────────┘

Optional integration modules (separate artifacts, future):
  quarkus-tarkus-flow     →  TaskExecutorFactory SPI (Quarkus-Flow)
  quarkus-tarkus-casehub  →  WorkerRegistry adapter (CaseHub)
  quarkus-tarkus-qhorus   →  MCP tools (Qhorus)
```

---

## WorkItem Model

```java
// io.quarkiverse.tarkus.runtime.model.WorkItem
@Entity @Table(name = "work_item")
public class WorkItem extends PanacheEntityBase {
    @Id public UUID id;

    public String title;
    public String description;

    @Enumerated(EnumType.STRING)
    public WorkItemStatus status;          // PENDING|ASSIGNED|IN_PROGRESS|COMPLETED|REJECTED|DELEGATED|ESCALATED|EXPIRED

    @Enumerated(EnumType.STRING)
    public WorkItemPriority priority;      // LOW|NORMAL|HIGH|CRITICAL

    public String assigneeId;             // Tarkus worker ID (human or agent)
    public String requiredCapabilities;   // comma-separated for routing
    public String createdBy;              // system or agent that created it
    public String delegationChain;        // comma-separated prior assignees

    @Column(columnDefinition = "TEXT")
    public String payload;                // JSON context for the human to act on

    @Column(columnDefinition = "TEXT")
    public String resolution;             // JSON decision written by the human

    public Instant createdAt;
    public Instant updatedAt;
    public Instant expiresAt;             // null = use default-expiry-hours config
    public Instant assignedAt;
    public Instant completedAt;
}

// io.quarkiverse.tarkus.runtime.model.AuditEntry
@Entity @Table(name = "audit_entry")
public class AuditEntry extends PanacheEntityBase {
    @Id public UUID id;
    public UUID workItemId;
    public String event;                  // CREATED|ASSIGNED|CLAIMED|DELEGATED|COMPLETED|REJECTED|ESCALATED|EXPIRED
    public String actor;                  // who performed the action
    public String detail;                 // JSON detail (e.g., previous assignee on delegation)
    public Instant occurredAt;
}
```

**WorkItemStatus lifecycle:**
```
PENDING → ASSIGNED → IN_PROGRESS → COMPLETED
                   ↘               ↘
                    DELEGATED        REJECTED
                    (→ PENDING for new assignee)

PENDING/ASSIGNED/IN_PROGRESS → EXPIRED (by ExpiryCleanupJob)
EXPIRED → ESCALATED (by EscalationPolicy SPI)
```

---

## REST API

Base path: `/tarkus/workitems`

| Method | Path | Description |
|---|---|---|
| `POST` | `/` | Create a WorkItem |
| `GET` | `/inbox` | List WorkItems for `?assignee=X&status=PENDING&priority=HIGH` |
| `GET` | `/{id}` | Get full WorkItem with audit log |
| `PUT` | `/{id}/claim` | Claim — assign to self (must be PENDING) |
| `PUT` | `/{id}/start` | Start work (ASSIGNED → IN_PROGRESS) |
| `PUT` | `/{id}/complete` | Complete with resolution JSON body |
| `PUT` | `/{id}/reject` | Reject with reason |
| `PUT` | `/{id}/delegate` | Delegate to `?to=assigneeId` |
| `GET` | `/` | List all WorkItems (admin) |

**Create request body:**
```json
{
  "title": "Review auth-refactor analysis",
  "description": "Alice's security analysis needs sign-off before proceeding",
  "assigneeId": "alice",
  "priority": "HIGH",
  "expiresAt": "2026-04-15T12:00:00Z",
  "payload": { "analysisRef": "uuid-of-shared-data-artefact", "channelName": "auth-refactor" }
}
```

---

## Configuration

`TarkusConfig` — `@ConfigMapping(prefix = "quarkus.tarkus")`:

| Property | Default | Meaning |
|---|---|---|
| `quarkus.tarkus.default-expiry-hours` | 24 | Hours before a WorkItem expires if no `expiresAt` is set |
| `quarkus.tarkus.escalation-policy` | notify | What happens on expiry: `notify`, `reassign`, `auto-reject` |
| `quarkus.tarkus.cleanup.expiry-check-seconds` | 60 | How often the expiry job runs |

Consuming app owns datasource config — none in the extension's `application.properties`.

---

## Escalation Policy SPI

```java
// io.quarkiverse.tarkus.runtime.service.EscalationPolicy
public interface EscalationPolicy {
    /** Called when a WorkItem's expiresAt passes without resolution. */
    void escalate(WorkItem workItem);
}
```

Default implementations (selectable via `quarkus.tarkus.escalation-policy`):
- `notify` — emits a `workitem.expired` CloudEvent; human must act
- `reassign` — moves to next assignee in a capability pool
- `auto-reject` — auto-rejects and records in audit log

Custom implementations register as CDI beans with `@Singleton @Alternative @Priority(1)`.

---

## CloudEvent Emission

Tarkus emits CloudEvents for all lifecycle transitions (via Quarkus Messaging):

| Event type | When |
|---|---|
| `io.quarkiverse.tarkus.workitem.created` | WorkItem created |
| `io.quarkiverse.tarkus.workitem.assigned` | WorkItem claimed or assigned |
| `io.quarkiverse.tarkus.workitem.completed` | WorkItem completed |
| `io.quarkiverse.tarkus.workitem.rejected` | WorkItem rejected |
| `io.quarkiverse.tarkus.workitem.delegated` | WorkItem delegated |
| `io.quarkiverse.tarkus.workitem.expired` | WorkItem expired |
| `io.quarkiverse.tarkus.workitem.escalated` | Escalation policy fired |

---

## Integration Modules (Future)

### quarkus-tarkus-flow
Implements `io.serverlessworkflow.impl.executors.TaskExecutorFactory` (Java SPI via
`META-INF/services`). When a Quarkus-Flow workflow step matches the Tarkus handler
(e.g., a custom `humanTask` type), the factory:
1. Creates a Tarkus WorkItem from the step definition
2. Suspends the WorkflowInstance (returns an incomplete CompletableFuture)
3. Completes the CompletableFuture with the WorkItem resolution when the human acts
4. Quarkus-Flow resumes the workflow with the resolution as output

### quarkus-tarkus-casehub
Registers a worker with CaseHub's `WorkerRegistry` claiming `human:*` capability tasks.
When a CaseHub Task is claimed:
1. Creates a Tarkus WorkItem with the CaseHub task context as payload
2. On WorkItem completion, calls `WorkerRegistry.submitResult()` to advance the case

### quarkus-tarkus-qhorus
Adds MCP tools backed by the Tarkus REST API:
- `request_approval(title, description, assignee, payload, timeout_s)` → creates WorkItem, returns `workItemId`
- `check_approval(work_item_id)` → returns status and resolution
- `wait_for_approval(work_item_id, timeout_s)` → polls until resolved or timeout

---

## Build Roadmap

| Phase | Status | What |
|---|---|---|
| **1 — Core data model** | ⬜ Pending | WorkItem + AuditEntry entities, Flyway V1, WorkItemService, TarkusConfig |
| **2 — REST API** | ⬜ Pending | WorkItemResource — all CRUD + inbox + lifecycle endpoints |
| **3 — Lifecycle engine** | ⬜ Pending | ExpiryCleanupJob, EscalationPolicy SPI, default implementations |
| **4 — CloudEvents** | ⬜ Pending | Event emission on all lifecycle transitions |
| **5 — Quarkus-Flow integration** | ⬜ Pending | `quarkus-tarkus-flow` module, TaskExecutorFactory SPI |
| **6 — CaseHub integration** | ⬜ Pending | `quarkus-tarkus-casehub` module, WorkerRegistry adapter |
| **7 — Qhorus integration** | ⬜ Pending | `quarkus-tarkus-qhorus` module, MCP tools |
| **8 — Native image validation** | ⬜ Pending | GraalVM native build, reflection config, native tests |

---

*This specification emerged from design discussions during the quarkus-qhorus project (2026-04-14), which identified the need for a standalone human task layer across CaseHub, Quarkus-Flow, and Qhorus.*
