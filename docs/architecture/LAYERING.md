# Layering Architecture
**Date:** 2026-04-23  
**Scope:** quarkus-work, quarkus-workitems, CaseHub, Quarkus-Flow ecosystem

---

## Work, WorkItem, and Task — the taxonomy

**Work** is the generic concept: anything that needs doing. The `quarkus-work-api` and `quarkus-work-core` modules define Work at this generic level — SPIs, relation types, event types, selection strategies — without assuming what kind of Work is involved.

**WorkItem** is a specific kind of Work: work that sits in a **human inbox**. A WorkItem is claimed by a person, worked on by a person, and completed by a person with a resolution. It has SLA, delegation, escalation, audit trail, and assignment routing. WorkItems live in the `runtime/` module of quarkus-work.

**Task** is another specific kind of Work: an **automated workflow step** executed by a machine. Task is owned by Quarkus-Flow and maps directly to the CNCF Serverless Workflow SDK `Task` type. The name cannot change — it is fixed by the specification.

```
Work  (generic concept — quarkus-work-api / quarkus-work-core)
 ├── WorkItem   (human inbox — quarkus-work runtime/)
 └── Task       (automated step — Quarkus-Flow)
```

A WorkItem is a type of Work. A Task is a type of Work. They are peers, not hierarchically related to each other. CaseHub coordinates Work — which may include WorkItems, Tasks, or future Work types — without needing to know which kind it is dealing with at the coordination level.

---

## The layers

```
┌─────────────────────────────────────────────────────────┐
│                        CaseHub                          │
│   case orchestration — blackboard + CMMN                │
│   owns: when, which, what-it-means                      │
└────────────────────────┬────────────────────────────────┘
                         │ calls / observes
┌────────────────────────┴────────────────────────────────┐
│                     quarkus-work                        │
│   human task primitives — inbox + lifecycle             │
│   owns: how WorkItems are created, managed, related     │
└────────────────────────┬────────────────────────────────┘
                         │ parallel peer
┌────────────────────────┴────────────────────────────────┐
│                    Quarkus-Flow                         │
│   workflow execution — CNCF Serverless Workflow         │
│   owns: automated Task execution                        │
└─────────────────────────────────────────────────────────┘
```

quarkus-work and Quarkus-Flow are peers at the Work execution layer. CaseHub sits above both and coordinates between them. Neither quarkus-work nor Quarkus-Flow knows about the other directly — CaseHub is the only thing that sees both.

---

## What quarkus-work owns

quarkus-work is the **human task primitive layer**. It provides the mechanics of managing WorkItems. It makes no decisions about what events mean or what should happen next.

**WorkItem lifecycle:**
- Create, assign, claim, start, complete, cancel, expire, escalate, delegate
- SLA enforcement — expiry deadlines, claim deadlines
- Status transitions and the guards on them

**Assignment and routing:**
- Candidate users and groups
- Selection strategies (`LeastLoadedStrategy`, `SemanticWorkerSelectionStrategy`, etc.)
- Skill profiles and embedding-based matching
- Workload awareness

**Relationships:**
- `PART_OF` and other Work relation types — the graph of how Work is related
- Parent/child linking via `WorkRelation`
- Cycle detection on PART_OF graphs

**Spawn mechanics:**
- Creating child WorkItems from templates on explicit request
- Wiring children to a parent via `PART_OF`
- Carrying `correlationId` opaquely on spawned children — stored and echoed, never interpreted
- Firing per-child lifecycle events

**Events:**
- `WorkItemLifecycleEvent` per state transition — fired, not acted on
- quarkus-work fires events; it does not react to them to drive other WorkItems

**Templates:**
- Blueprints for WorkItem creation — category, priority, groups, SLA, labels
- Instantiation from template in a single API call

**Queues and filters:**
- Inbox organisation — filter chains, queue views, label-based routing
- `FilterRegistryEngine` — applies rules to WorkItems as they transition

**Audit and ledger:**
- Append-only audit trail per WorkItem
- Ledger entries with causal chain (`causedByEntryId`) for provenance

---

## What quarkus-work does NOT own

quarkus-work **does not orchestrate**. It does not decide what happens next based on events. Specifically:

- **When to spawn WorkItems** — that is the caller's decision (CaseHub, application)
- **What completing a WorkItem means** — quarkus-work fires the event; CaseHub decides
- **Completion rollup** — whether a group of children satisfies a stage; that is CaseHub's `Stage.requiredItemIds` and autocomplete logic
- **Parent auto-completion** — quarkus-work does not complete a parent when children finish; CaseHub does
- **Activation conditions** — whether a child should activate based on parent state; that is CaseHub's blackboard
- **Milestones** — case-level checkpoints; CaseHub's CMMN model
- **Case lifecycle** — fault handling, case cancellation semantics; CaseHub
- **What a rejected or expired WorkItem means for a process** — CaseHub decides

If quarkus-work were to implement any of these, it would be competing with CaseHub. The rule is: **quarkus-work fires events and provides primitives. Every "what happens next" decision lives above it.**

---

## What CaseHub owns

CaseHub is the **case orchestration layer**. It coordinates Work — which may be WorkItems, Tasks, or future Work types — according to a `CasePlanModel`.

- **When to create WorkItems** — CaseHub calls `POST /workitems/{id}/spawn` or creates individual WorkItems when bindings fire
- **Which children to spawn** — CaseHub's `CasePlanModel` defines the structure; quarkus-work executes it
- **What completing a WorkItem means** — CaseHub's `PlanItemCompletionHandler` marks the corresponding `PlanItem` COMPLETED and evaluates stage autocomplete
- **Stage completion** — `Stage.requiredItemIds` + autocomplete; not quarkus-work's concern
- **Case-level fault handling** — REJECTED child → CaseHub's goal evaluation decides whether to fault the case
- **Cancellation semantics** — when a stage terminates, CaseHub instructs quarkus-work to cancel children; quarkus-work executes, does not decide
- **Activation conditions** — CaseHub's blackboard evaluates whether a planned child should activate now
- **Orchestration vs choreography** — CaseHub chooses the execution model; quarkus-work supports both via `correlationId` on lifecycle events

---

## The boundary rule

> **quarkus-work provides primitives and fires events.**  
> **CaseHub consumes primitives and makes decisions.**  
> **Nothing crosses this line in either direction.**

A concrete test: if a piece of code in quarkus-work says "when X happens, do Y to another WorkItem," it is orchestration and belongs in CaseHub or the application. quarkus-work is allowed to say "when X happens to a WorkItem, update that WorkItem's own state" — that is lifecycle management, not orchestration.

Examples:
- "When a WorkItem expires, mark it EXPIRED" — **lifecycle management, quarkus-work** ✓
- "When a WorkItem expires, escalate it to a supervisor group" — **lifecycle management, quarkus-work** ✓ (EscalationPolicy)
- "When all children complete, complete the parent" — **orchestration, CaseHub** ✗ not quarkus-work
- "When a WorkItem reaches ASSIGNED, spawn three children" — **orchestration, CaseHub** ✗ not quarkus-work
- "When a child is rejected, block parent completion" — **orchestration, CaseHub** ✗ not quarkus-work

---

## How they interact

**CaseHub → quarkus-work (commands):**
```
POST /workitems                    create a WorkItem
POST /workitems/{id}/spawn         spawn children, link via PART_OF
PATCH /workitems/{id}/assign       assign to an actor
DELETE /workitems/{id}             cancel
GET   /workitems/{id}/children     query PART_OF children
```

**quarkus-work → CaseHub (events):**
```
WorkItemLifecycleEvent(CREATED,   workItemId, correlationId, ...)
WorkItemLifecycleEvent(ASSIGNED,  workItemId, correlationId, ...)
WorkItemLifecycleEvent(COMPLETED, workItemId, correlationId, resolution, ...)
WorkItemLifecycleEvent(REJECTED,  workItemId, correlationId, ...)
WorkItemLifecycleEvent(EXPIRED,   workItemId, correlationId, ...)
WorkItemLifecycleEvent(SPAWNED,   workItemId, correlationId, childIds, ...)
```

`correlationId` is the routing key: CaseHub embeds it at spawn time (e.g. `caseId:planItemId`), quarkus-work stores it opaquely on the WorkItem and echoes it in every lifecycle event. CaseHub's adapter uses it to route completion back to the right `CasePlanModel` and `PlanItem` without a query.

**The `quarkus-work-casehub` integration module** (future) is the translation layer: it observes `WorkItemLifecycleEvent`, extracts `correlationId`, and signals `CONTEXT_CHANGED` on the right `CaseInstance`. Neither quarkus-work nor CaseHub knows about the other — the adapter bridges them.

---

## Why this matters

**No competing functionality.** If quarkus-work implements completion rollup, it competes with CaseHub's `Stage` autocomplete. Two systems making the same decision with potentially different answers is worse than one system making it correctly.

**Clean buildability.** CaseHub can build on quarkus-work without working around it. quarkus-work does not have opinions that CaseHub needs to suppress with config flags.

**Independent evolution.** quarkus-work can improve assignment routing, skill matching, SLA mechanics without CaseHub needing to change. CaseHub can improve case orchestration, blackboard evaluation, planning strategies without quarkus-work needing to change. The events and REST API are the stable contract between them.

**Reusability.** Any application — with or without CaseHub — can use quarkus-work for human task management. The primitives are useful standalone. CaseHub adds orchestration on top; it does not replace the primitives.

**Intuitive taxonomy.** A developer reading either codebase knows immediately what it owns. quarkus-work answers: "how is this WorkItem created, assigned, tracked, and completed?" CaseHub answers: "what work needs to happen in this case, and in what order?"
