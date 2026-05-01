# casehub-work

A CaseHub library that gives any Quarkus application a **runtime human task layer** — units of work that wait for a human or AI agent to act on them, with expiry, delegation, escalation, priority, candidate group routing, and a full audit trail. One dependency. Zero coupling to your domain.

---

## Why not just use GitHub Issues?

The short answer: GitHub Issues live outside your application. WorkItems live inside it.

When a loan approval WorkItem completes, the approval and the WorkItem state update happen in the **same database transaction**. If the payment fails, both roll back. You cannot do that with a GitHub Issue — it's an HTTP call to an external system, outside your transaction boundary, with no rollback.

Beyond transactions: WorkItems fire CDI events when they expire, enforce a 10-state lifecycle with guarded transitions, route to candidate groups for self-service claiming, support full delegation chains with ownership tracking, and carry an append-only audit trail. None of this is available in an issue tracker.

→ **[Full argument: WorkItems vs Issue Trackers](docs/workitems-vs-issue-trackers.md)**

---

## Quick start

### 1. Add the dependency

```xml
<dependency>
  <groupId>io.casehub</groupId>
  <artifactId>casehub-work</artifactId>
  <version>0.2-SNAPSHOT</version>
</dependency>
```

### 2. Configure a datasource

WorkItems manages its own schema via Flyway but uses whatever datasource your application provides:

```properties
quarkus.datasource.db-kind=postgresql
quarkus.datasource.jdbc.url=jdbc:postgresql://localhost/myapp
quarkus.datasource.username=myuser
quarkus.datasource.password=mypassword
quarkus.flyway.migrate-at-start=true
```

### 3. Use the REST API

```bash
# Create a WorkItem
curl -X POST http://localhost:8080/workitems \
  -H 'Content-Type: application/json' \
  -d '{"title":"Approve loan application #4821","priority":"HIGH","createdBy":"underwriting-agent","candidateGroups":"loan-officers"}'

# Claim it (PENDING → ASSIGNED)
curl -X PUT "http://localhost:8080/workitems/{id}/claim" \
  -H 'Content-Type: application/json' \
  -d '{"assigneeId":"alice"}'

# Complete it (IN_PROGRESS → COMPLETED)
curl -X PUT "http://localhost:8080/workitems/{id}/complete" \
  -H 'Content-Type: application/json' \
  -d '{"actorId":"alice","resolution":"{\"approved\":true}","rationale":"Income verified, DTI within limits"}'
```

### 4. Observe lifecycle events

```java
@ApplicationScoped
public class LoanApprovalHandler {

    @Observes
    void onWorkItemEvent(WorkItemLifecycleEvent event) {
        if ("completed".equals(event.type().substring(event.type().lastIndexOf('.') + 1))) {
            // react in the same transaction as the completion
        }
    }
}
```

---

## For humans and agents alike

WorkItems does not require a human resolver. The `assigneeId` is a string — `"alice"`, `"agent-007"`, or any AI agent identity. The value is the **waiting infrastructure**: deadlines, escalation, delegation, audit. Whether the resolver is human or algorithmic, your application needs a structured boundary where asynchronous work is tracked and enforced.

The meaningful distinction is not human vs agent — it is **synchronous vs asynchronous resolution**. A machine task in Quarkus-Flow executes in milliseconds and never waits. A WorkItem waits — minutes, hours, days — and that waiting needs management.

---

## WorkItem lifecycle

```
                PENDING ──── claim ────► ASSIGNED ──── start ────► IN_PROGRESS
                  ▲              │                          │
                  │           release                  complete │ reject
                  │              │                       │         │
                  └──── delegate ┘                    COMPLETED  REJECTED
                                                       (terminal)

ASSIGNED | IN_PROGRESS ──── suspend ───► SUSPENDED ──── resume ───► (prior state)
ASSIGNED | IN_PROGRESS ──── delegate ──► PENDING (new assignee)
Any non-terminal ──────── cancel ──────► CANCELLED (terminal)
PENDING | ASSIGNED | IN_PROGRESS | SUSPENDED ── (deadline breach) ──► EXPIRED ──► ESCALATED
```

**Delegation detail:** on first delegation the actor becomes `owner`. Subsequent delegates extend `delegationChain`. The WorkItem returns to PENDING for the next assignee.

---

## Why "WorkItem" not "Task"

Three systems in the Quarkus ecosystem define "task":

| Term | System | Meaning |
|---|---|---|
| `Task` | CNCF Serverless Workflow / Quarkus-Flow | Machine-executed step — milliseconds, no assignee, no expiry |
| `Task` | CaseHub | CMMN case work unit — assigned to any worker via capabilities |
| `WorkItem` | casehub-work | Asynchronous unit awaiting resolution — minutes to days, assignee, expiry, delegation, audit |

**Rule:** a `Task` is controlled by a machine. A `WorkItem` waits for resolution.

---

## Modules

| Artifact | Status | Purpose |
|---|---|---|
| `casehub-work` | Core | WorkItem model, JPA storage, REST API, lifecycle engine, CDI events, labels, vocabulary |
| `casehub-work-testing` | Core | `InMemoryWorkItemStore` + `InMemoryAuditEntryStore` for unit tests without a datasource |
| `casehub-work-queues` | Optional | Label-based work queues — JEXL/JQ/Lambda filters, FilterChain, QueueView, soft assignment, queue lifecycle events (ADDED/REMOVED/CHANGED) |
| `casehub-work-ledger` | Optional | Accountability — command/event ledger, SHA-256/MMR hash chain, peer attestation, EigenTrust reputation scoring |
| `casehub-work-persistence-mongodb` | Optional | MongoDB `WorkItemStore` + `AuditEntryStore`. Drop-in replacement for JPA defaults |
| `casehub-work-issue-tracker` | Optional | Link WorkItems to GitHub Issues, Jira, Linear, etc. Pluggable `IssueTrackerProvider` SPI |
| `casehub-work-postgres-broadcaster` | Optional | Distributed SSE — PostgreSQL LISTEN/NOTIFY backend for `WorkItemEventBroadcaster`. Delivers lifecycle events to SSE clients on all cluster nodes. Drop-in replacement: add the dependency, the broadcaster auto-activates via `@Alternative @Priority(1)`. |
| `casehub-work-flow` | Integration | Quarkus-Flow `WorkItemsFlow` base class — `workItem()` DSL alongside `function()`, `agent()` |

---

## Configuration

| Property | Default | Description |
|---|---|---|
| `casehub.work.default-expiry-hours` | `24` | Completion deadline when none supplied at creation |
| `casehub.work.default-claim-hours` | `4` | Claim deadline for unclaimed WorkItems. `0` = no deadline |
| `casehub.work.escalation-policy` | `notify` | On `expiresAt` breach: `notify`, `reassign`, or `auto-reject` |
| `casehub.work.claim-escalation-policy` | `notify` | On `claimDeadline` breach: `notify` or `reassign` |
| `casehub.work.cleanup.expiry-check-seconds` | `60` | Polling interval for the expiry/claim-deadline job |

---

## Documentation

- [**Why WorkItems vs Issue Trackers**](docs/workitems-vs-issue-trackers.md) — the case for a runtime human task layer
- [**Architecture**](docs/ARCHITECTURE.md) — module graph, domain model, SPI contracts, ecosystem positioning
- [**API Reference**](docs/api-reference.md) — all REST endpoints, request/response schemas, CDI event types
- [**Implementation Tracker**](docs/DESIGN.md) — build roadmap and test totals

---

## Ecosystem

WorkItems is the independent human task layer in the Quarkus Native AI Agent Ecosystem alongside Quarkus-Flow (workflow), CaseHub (case orchestration), and Qhorus (agent mesh). WorkItems has no dependency on any of them — optional integration modules depend on WorkItems, not vice versa.

---

## License

Apache 2.0
