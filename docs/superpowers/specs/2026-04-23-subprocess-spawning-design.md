# Subprocess Spawning — Design Draft
**Date:** 2026-04-23  
**Status:** DRAFT — awaiting CaseHub review  
**Epic:** #105

---

## What this document is

A draft positioning of subprocess spawning in quarkus-work, written to be challenged by CaseHub. The core question is: where does the boundary sit between quarkus-work (mechanical primitive layer) and CaseHub (blackboard + CMMN orchestration layer)?

---

## Positioning

### quarkus-work is the primitive layer

quarkus-work provides WorkItems — units of human work with lifecycle, assignment, audit, and SLA. It does not orchestrate between WorkItems. It does not decide when to create them or what their completion means to a larger process.

### CaseHub is the orchestration layer

CaseHub owns case state, activation conditions, milestones, and the flexible process model (CMMN + blackboard). It decides *when* to spawn children, *which* children, *under what conditions* they activate, and *what completing a child means* for the case.

### The boundary for #105

Subprocess spawning in quarkus-work is **the mechanical execution layer** that CaseHub (and simpler callers) drives. quarkus-work:

- Creates child WorkItems from templates on request
- Wires them to a parent via `PART_OF`
- Tracks the completion state of a child group
- Fires a CDI event when a group reaches a terminal collective state

quarkus-work does **not**:

- Decide when to spawn (that's the caller's job)
- Apply activation conditions to individual children (CaseHub's blackboard does this)
- Know what completing a child group means for the overall case (CaseHub owns that)
- Implement flexible process patterns (milestones, ad-hoc fragments, case lifecycle)

---

## What quarkus-work provides

### 1. Caller-driven spawn API

```
POST /workitems/{parentId}/spawn
```

Request body: a list of child specifications — either template references or inline `WorkItemCreateRequest` payloads.

```json
{
  "children": [
    { "templateId": "credit-check-template" },
    { "templateId": "fraud-check-template",  "overrides": { "candidateGroups": "fraud-team" } },
    { "templateId": "compliance-check-template" }
  ],
  "completionPolicy": "ALL_MUST_COMPLETE"
}
```

Response: IDs of the created child WorkItems and the spawn group ID.

The caller (CaseHub, Quarkus-Flow, or a simple application) calls this explicitly. quarkus-work does not decide when.

**Open question for CaseHub:** Is `completionPolicy` something quarkus-work should know about, or should it be entirely CaseHub's concern? If CaseHub observes child completion events directly, quarkus-work may not need to track this at all.

### 2. Template-level spawn config (simple case)

For applications that don't use CaseHub, the `WorkItemTemplate` can embed a spawn config:

```json
{
  "name": "loan-application",
  "spawnConfig": {
    "triggerOn": "ASSIGNED",
    "children": [
      { "templateId": "credit-check-template" },
      { "templateId": "fraud-check-template" },
      { "templateId": "compliance-check-template" }
    ],
    "completionPolicy": "ALL_MUST_COMPLETE"
  }
}
```

When the loan-application WorkItem reaches ASSIGNED, the `SpawnEngine` (CDI observer) fires automatically. This is the built-in simple case. CaseHub would bypass this and use the caller-driven API instead.

**Open question for CaseHub:** Does CaseHub ever want quarkus-work to auto-trigger spawning, or does CaseHub always want to be the explicit caller?

### 3. Spawn group entity

A `WorkItemSpawnGroup` tracks a set of PART_OF children created together:

```
WorkItemSpawnGroup {
  id: UUID
  parentId: UUID
  childIds: UUID[]
  completionPolicy: ALL_MUST_COMPLETE | M_OF_N | NONE
  completionThreshold: int   (for M_OF_N)
  status: ACTIVE | COMPLETE | CANCELLED
  createdAt: Instant
  completedAt: Instant?
}
```

quarkus-work updates this as children complete. CaseHub can query it or observe the event.

**Open question for CaseHub:** Does CaseHub need quarkus-work to maintain spawn group state, or does CaseHub maintain its own case state (blackboard) and just use the raw PART_OF graph to reconstruct group state when needed?

### 4. Completion event

When a spawn group reaches its terminal state (all required children COMPLETED, or M-of-N threshold met), quarkus-work fires:

```java
@ApplicationScoped
public class SpawnGroupCompletedEvent {
    UUID spawnGroupId;
    UUID parentWorkItemId;
    List<UUID> completedChildIds;
    CompletionPolicy policy;
}
```

CaseHub observes this event to decide next steps (advance case, trigger next stage, complete parent, etc.).

**Open question for CaseHub:** What does CaseHub need in this event payload? Is the parent WorkItem ID sufficient, or does CaseHub need the full spawn group state?

### 5. Optional parent auto-completion

If `completionPolicy = ALL_MUST_COMPLETE` and the parent WorkItem is in a state that allows completion, quarkus-work can optionally auto-complete the parent when the group completes. This is off by default for CaseHub deployments (CaseHub controls parent lifecycle) and on by default for standalone deployments.

Config: `quarkus.work.spawn.auto-complete-parent=false`

---

## REST API summary

| Method | Path | Description |
|---|---|---|
| `POST` | `/workitems/{id}/spawn` | Spawn children, create PART_OF links, return group ID |
| `GET` | `/workitems/{id}/spawn-groups` | List spawn groups for a parent WorkItem |
| `GET` | `/spawn-groups/{id}` | Get group status and child IDs |
| `DELETE` | `/spawn-groups/{id}` | Cancel a spawn group (mark CANCELLED, does not cancel children) |
| `GET` | `/workitems/{id}/children` | (existing) PART_OF children — unchanged |

---

## Test strategy (TDD)

Given the user's requirement for extensive TDD, tests are organised by concern. Every layer is independently testable.

### Unit tests (no Quarkus, no DB)

**SpawnEngine logic:**
- Happy path: given parent + spawn config → correct child `WorkItemCreateRequest` objects produced
- Template override merging: overrides correctly win over template defaults
- Trigger condition matching: only fires on matching triggerOn status
- Idempotency guard: second spawn on same parent+trigger does not double-spawn

**CompletionRollupService logic:**
- ALL_MUST_COMPLETE: fires only when last child completes
- M_OF_N: fires at threshold, not before, not on subsequent completions
- NONE policy: never fires auto-complete
- Handles CANCELLED children correctly (do they count toward completion?)
- Handles REJECTED children (do they block completion or are they terminal?)
- Concurrent completion: what if two children complete simultaneously?

**SpawnGroup state machine:**
- ACTIVE → COMPLETE transition guards
- ACTIVE → CANCELLED: what happens to in-flight children?
- Already-COMPLETE group: subsequent child completion events are no-ops

### Integration tests (@QuarkusTest, H2)

**Spawn API:**
- POST /workitems/{id}/spawn with valid templates → 201, children created, PART_OF links verified, group ID returned
- POST with invalid templateId → 422
- POST on non-existent parent → 404
- POST on terminal parent (COMPLETED/CANCELLED) → 409 or configurable
- GET /spawn-groups/{id} → returns group with correct child IDs and status
- DELETE /spawn-groups/{id} → group CANCELLED, children unchanged

**SpawnEngine (template-driven):**
- WorkItem created from spawning template, reaches triggerOn status → children auto-created
- triggerOn=ASSIGNED: verify fires on claim, not on create
- Spawn config missing templateId → logged error, parent unaffected
- No spawnConfig on template → no children spawned (no-op)

**CompletionRollup:**
- All children COMPLETED → SpawnGroupCompletedEvent fired, group status = COMPLETE
- M_OF_N threshold met → event fired, remaining children continue in PENDING/ASSIGNED
- One child REJECTED → configurable: blocks or is treated as terminal
- Manual cancel of a child → does not trigger rollup (CANCELLED ≠ COMPLETED)

### End-to-end tests (@QuarkusTest, scenario-style)

**Happy path — all parallel:**
1. Create loan-application from template → auto-spawns credit-check, fraud-check, compliance-check
2. Complete all three children
3. Assert SpawnGroupCompletedEvent fired
4. Assert parent auto-completes (if auto-complete-parent=true)
5. Assert audit trail on parent records SPAWN event

**Happy path — M_OF_N:**
1. Create parent with spawn config: 3 children, M_OF_N threshold=2
2. Complete 2 of 3 children
3. Assert group COMPLETE event fires after 2nd completion
4. Assert 3rd child still PENDING (not auto-cancelled)

**Caller-driven (CaseHub pattern):**
1. Create parent WorkItem (no template spawn config)
2. POST /workitems/{id}/spawn with 3 template IDs
3. Verify 3 children created and linked via PART_OF
4. Complete children one at a time
5. Verify group completion event fires correctly

**Robustness:**
- Spawn called twice on same parent: idempotency response (return existing group or 409)
- Parent cancelled mid-spawn: children created before cancellation remain, group goes CANCELLED
- Child template deleted after spawn: existing child WorkItems unaffected
- SpawnGroup completion event observer throws: error logged, group state still updated

### Correctness tests

**PART_OF graph integrity:**
- Children are linked child→parent (not parent→child)
- Cyclic PART_OF prevention still enforced (existing guard)
- Nested spawn: child from one group can itself be a parent — verify correct group resolution

**Concurrency:**
- Two children completing in the same millisecond: only one SpawnGroupCompletedEvent fires
- Optimistic lock on SpawnGroup prevents double-completion

**Completion policy edge cases:**
- ALL_MUST_COMPLETE with 0 children: group immediately COMPLETE
- M_OF_N with threshold > child count: group never auto-completes (caller must cancel)
- M_OF_N threshold = 0: invalid, rejected at POST time

---

## Open questions for CaseHub

1. **Who maintains spawn group state?** quarkus-work's `WorkItemSpawnGroup` entity, or CaseHub's blackboard? If CaseHub tracks case state separately, maintaining it in two places creates sync risk.

2. **Does CaseHub ever use template-driven auto-spawn?** Or does CaseHub always call the spawn API explicitly? If always explicit, the template-level spawn config is purely for non-CaseHub deployments.

3. **What does CaseHub need in the completion event?** Is `parentWorkItemId` + `spawnGroupId` sufficient for CaseHub to look up its own case state, or does it need more payload?

4. **REJECTED children and completion policy:** From CaseHub's perspective, if one of three parallel checks is rejected (e.g., fraud check fails), should that block ALL_MUST_COMPLETE? Or does CaseHub handle that logic and quarkus-work should treat REJECTED as terminal (counts toward completion threshold)?

5. **Cancellation semantics:** If CaseHub cancels a spawn group (DELETE /spawn-groups/{id}), should quarkus-work also cancel all PENDING children? Or leave them for CaseHub to cancel individually?

6. **Spawn group visibility:** Does CaseHub need quarkus-work to expose `GET /workitems/{id}/spawn-groups`? Or will CaseHub reconstruct group membership from the PART_OF graph itself?

---

## What is explicitly NOT in scope

- Activation conditions on individual children (CaseHub's blackboard)
- Milestones (CaseHub's CMMN model)
- Ad-hoc fragment activation (CaseHub's case lifecycle)
- Sequential chaining (deferred — API accommodates it, implementation deferred)
- Cross-service spawn (distributed WorkItems epic #92)
- Spawn rule hot-swap / runtime topology mutation (CaseHub's domain)
