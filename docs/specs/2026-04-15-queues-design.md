# Quarkus WorkItems — Labels, Filters, and Queues Design

> *This document covers the label model, vocabulary, filter engine, and queue
> views. The queues feature is an optional module — `quarkus-workitems-queues`
> — built on top of label infrastructure in the core extension.*

---

## Design Principle: Labels Are Queues

A queue is not a separate entity. A queue is a **label** viewed through a
**named query**. Any WorkItem carrying that label is in that queue. The filter
engine decides which labels a WorkItem carries. The queue view decides how to
display them.

This keeps the core model simple: one set of labels per WorkItem, one filter
engine that maintains them, one query per queue view.

---

## Module Boundary

| Concern | Module |
|---|---|
| `WorkItemLabel` type | `quarkus-workitems` core |
| `labels` collection on `WorkItem` | `quarkus-workitems` core |
| `LabelVocabulary` + `LabelDefinition` | `quarkus-workitems` core |
| Label REST endpoints (add, remove, query by label) | `quarkus-workitems` core |
| `WorkItemFilter` (conditions + actions) | `quarkus-workitems-queues` |
| Filter evaluation engine | `quarkus-workitems-queues` |
| `QueueView` (named query over a label) | `quarkus-workitems-queues` |
| Queue REST endpoints | `quarkus-workitems-queues` |

The integration seam is `WorkItemLifecycleEvent` — already fired by
`WorkItemService` on every creation and mutation. The queue module observes
this event to trigger filter re-evaluation. If the module is absent, the event
fires into the void. The core is unchanged.

```
quarkus-workitems-parent
├── quarkus-workitems                  ← core (labels, vocabulary)
├── quarkus-workitems-deployment
├── quarkus-workitems-testing
├── quarkus-workitems-flow
├── quarkus-workitems-ledger
└── quarkus-workitems-queues           ← optional (filters, queue views)
```

---

## Label Model

### WorkItemLabel

Each WorkItem carries a single ordered collection of labels:

```java
// WorkItem.java
List<WorkItemLabel> labels;  // 0..n entries
```

Each entry:

| Field | Type | Description |
|---|---|---|
| `path` | `String` | The label — a `/`-separated path, e.g. `legal`, `legal/contracts/nda` |
| `persistence` | `LabelPersistence` | `MANUAL` or `INFERRED` |
| `appliedBy` | `String` | userId (MANUAL) or filterId (INFERRED) — audit trail |

```java
public enum LabelPersistence {
    MANUAL,   // human-applied; only a human or explicit API call removes it
    INFERRED  // filter-applied; retracted and recomputed on every WorkItem mutation
}
```

### Label Path Semantics

A label is a `/`-separated sequence of terms. Each term is a single word.

```
legal                        ← single-term label
legal/contracts              ← two-segment path
legal/contracts/nda          ← three-segment path
```

A single-term label is identical in structure to a multi-segment one — there
is no distinction between "label" and "path label". The `/` separator is
purely a scoping convention.

**Wildcard matching** (used in filter conditions and queue queries):

| Pattern | Matches |
|---|---|
| `legal` | exactly `legal` |
| `legal/*` | one level below: `legal/contracts`, `legal/ip` — not `legal/contracts/nda` |
| `legal/**` | all descendants: `legal/contracts`, `legal/contracts/nda`, etc. |

### Relationship to `category`

`WorkItem.category` (existing field) is semantically equivalent to a MANUAL
label at the `category/<value>` path. It is kept as a dedicated field for
backwards compatibility and query convenience. Internally, it can be treated
as a reserved top-level namespace: filters matching `category/**` will match
any WorkItem whose `category` field is set.

A future version may unify `category` into the label collection entirely.

---

## Persistence Semantics

### MANUAL

- Applied by a human: at creation time, or via the label API post-creation.
- Removed only by a human (or an explicit API call).
- Never touched by the filter re-evaluation cycle.

### INFERRED

- Applied by the filter engine when a filter condition matches.
- On **any WorkItem mutation** (any field change, including label changes):
  1. All `INFERRED` entries are removed.
  2. All active INFERRED filters are evaluated against the WorkItem.
  3. Matching filters re-apply their labels as `INFERRED`.
- Result: INFERRED labels always reflect current truth.
- No explicit "remove label" action exists in the filter engine. A filter
  that should no longer apply simply doesn't match — the label is not
  re-inserted.

This eliminates filter conflicts: no two filters can disagree on a label
because there is no remove-action. A filter either matches (label present) or
doesn't (label absent).

---

## Vocabulary

Labels are **vocabulary-controlled**: a label path must be declared in a
`LabelVocabulary` before it can be applied to a WorkItem.

### Scopes

Vocabularies are organised in a hierarchy:

```
GLOBAL   ← platform-wide; accessible everywhere
  └── ORG      ← organisation-level
        └── TEAM     ← team-level
              └── PERSONAL  ← individual user
```

Each scope can reference labels from any higher scope. A PERSONAL vocabulary
can use terms from GLOBAL, ORG, and TEAM vocabularies. A TEAM vocabulary
cannot use PERSONAL terms.

### Open Contribution

Any user can contribute to any vocabulary at or below their access level:

- Org admin → ORG vocabulary
- Team member → TEAM vocabulary
- Any user → PERSONAL vocabulary

When a user attempts to apply an undeclared label, the system prompts:
> *"Label `legal/contracts/nda` is not in any vocabulary you have access to.
> Add it to: [TEAM: legal-team] [PERSONAL]?"*

### LabelDefinition

| Field | Description |
|---|---|
| `path` | The full label path |
| `vocabularyId` | Which vocabulary it belongs to |
| `description` | Human-readable description (optional) |
| `createdBy` | Who declared it |
| `createdAt` | When declared |

---

## Filter Model

Filters live in `quarkus-workitems-queues`. They are evaluated by observing
`WorkItemLifecycleEvent`.

### WorkItemFilter

| Field | Description |
|---|---|
| `id` | UUID |
| `name` | Human-readable name |
| `scope` | `PERSONAL`, `TEAM`, or `ORG` |
| `owner` | userId (PERSONAL) or groupId (TEAM/ORG) |
| `conditions` | List of predicates over WorkItem fields |
| `actions` | List of label-apply actions |
| `active` | Whether the filter runs automatically |

### Conditions

Conditions are predicates over WorkItem fields. Multiple conditions are
combined with AND by default (OR grouping is a future extension).

Filterable fields:

| Field | Match type |
|---|---|
| `status` | enum equality / set membership |
| `priority` | enum equality / comparison |
| `category` | string equality / prefix |
| `assigneeId` | string equality / null check |
| `candidateGroups` | contains |
| `labels` | path wildcard match (`legal/**`) |
| `title` / `description` | substring / keyword |
| `createdAt` / `expiresAt` | date range |

### Actions

For the initial release, one action type:

**`ApplyLabel`**
- `path` — label path to apply (must exist in an accessible vocabulary)
- Applied as `INFERRED` (filter-applied, recomputed on change)

No remove-label action exists. Removal of INFERRED labels is implicit
via re-evaluation.

### Filter Lifecycle

**Ad-hoc filters** — not persisted. The user constructs a condition set and
gets a live result set back. Equivalent to a parameterised query. Can be
promoted to a saved filter.

**Saved filters** — persisted, named, scoped. Run automatically on every
`WorkItemLifecycleEvent`. The filter's `active` flag can pause it without
deleting it.

### Evaluation Order

When multiple filters apply labels to the same WorkItem, all matching filters
apply their labels. There is no conflict because there is no remove-action.
Filter evaluation order affects nothing for the initial release. If ordering
becomes significant (e.g. for future action types), filters will gain an
explicit `priority` field.

---

## Queue Model

A **queue** is a label combined with a named view configuration. It is not a
stored entity separate from the label — it is a query.

### QueueView

| Field | Description |
|---|---|
| `id` | UUID |
| `name` | Human-readable name, e.g. "Unassigned legal — oldest first" |
| `labelPattern` | Label path or wildcard, e.g. `legal/contracts/**` |
| `scope` | `PERSONAL`, `TEAM`, or `ORG` |
| `owner` | userId or groupId |
| `filterConditions` | Additional predicates within the label (e.g. `status = PENDING`) |
| `sortOrder` | e.g. `createdAt ASC`, `priority DESC` |

**View ownership is independent of label ownership.** An ORG-scoped label
`legal/contracts` can have multiple team-owned views of it — one for the
contracts team sorted by creation date, one for triage sorted by priority.
Anyone can define a view over any label they can read.

### The Triage / Pickup Use Case

The primary use case for queues is **unassigned work**: a pool of WorkItems
waiting for someone to claim them.

A typical triage queue view:
```
labelPattern:       intake/**
filterConditions:   status = PENDING
sortOrder:          createdAt ASC    (oldest first)
```

When a user picks a WorkItem from this queue, the existing `claim` endpoint
is called (`PUT /workitems/{id}/claim`), transitioning it `PENDING → ASSIGNED`.
Once assigned, a filter with condition `status != PENDING` will no longer match
the WorkItem, so the `intake/**` INFERRED label is not re-applied — the item
leaves the queue automatically.

### Soft Assignment

A user may claim a WorkItem (ASSIGNED) without yet starting it (IN_PROGRESS),
signalling to others: "I have this, but feel free to take it."

This is modelled as a `relinquishable` flag on the WorkItem, managed by the
queues module (not core). A relinquishable ASSIGNED WorkItem is visible in
queue views with a visual indicator. Any eligible candidate can invoke
`PUT /workitems/{id}/claim` to take it (the queue module relaxes the normal
"already assigned" guard when `relinquishable = true`).

The assignee sets `relinquishable = true` via:
```
PUT /workitems/{id}/relinquishable
{ "relinquishable": true }
```

Setting `relinquishable = false` or starting work (`PUT /{id}/start`) clears it.

---

## REST API Surface

### Core additions (`quarkus-workitems`)

| Method | Path | Description |
|---|---|---|
| `GET` | `/workitems?label=legal/**` | Query WorkItems by label pattern |
| `POST` | `/workitems/{id}/labels` | Add a MANUAL label |
| `DELETE` | `/workitems/{id}/labels/{path}` | Remove a MANUAL label |
| `GET` | `/vocabulary` | List accessible label vocabularies |
| `POST` | `/vocabulary/{scope}` | Add a LabelDefinition to a vocabulary |

### Queues module (`quarkus-workitems-queues`)

| Method | Path | Description |
|---|---|---|
| `GET` | `/filters` | List filters visible to the caller |
| `POST` | `/filters` | Create a saved filter |
| `PUT` | `/filters/{id}` | Update a saved filter |
| `DELETE` | `/filters/{id}` | Delete a saved filter |
| `POST` | `/filters/evaluate` | Ad-hoc filter evaluation (not saved) |
| `GET` | `/queues` | List QueueViews accessible to the caller |
| `POST` | `/queues` | Create a QueueView |
| `GET` | `/queues/{id}` | WorkItems in this queue view |
| `PUT` | `/workitems/{id}/relinquishable` | Set soft-assignment flag |

---

## Key Design Decisions

### Labels are queues, not queue members

A label is not registered with a queue. The queue is a query against the label.
This means a new QueueView can be created over any existing label retroactively,
with no migration of WorkItem data.

### INFERRED labels have no remove-action

Removing explicit remove-actions from the filter engine eliminates filter
conflicts entirely. If a label should not be present, the filter condition
simply should not match. This keeps filter semantics predictable and
composable.

### Vocabulary is strict but open-contribution

Labels must be declared before use. This prevents label sprawl and enables
autocomplete in UIs. The open-contribution model (anyone can propose at their
scope level) keeps governance lightweight.

### `relinquishable` lives in the queues module

Soft assignment is a queue-pickup concept, not a core lifecycle concept. The
core WorkItem lifecycle (`PENDING → ASSIGNED → IN_PROGRESS`) remains
unchanged. The queues module extends pickup behaviour without modifying core.

---

## Open Items

| Item | Notes |
|---|---|
| Filter condition OR-grouping | Initial release uses AND-only; OR grouping deferred |
| Label vocabulary search / autocomplete API | Useful for UI; not required for first implementation |
| `category` unification | `category` field may eventually be sugar for a `category/**` MANUAL label |
| Notification actions | Future filter action type: notify a user or group when a filter matches |
| Webhook / CDI actions | Future: fire external event when filter matches |
| ProvenanceLink labels | PROV-O graph entries (issue #39) may carry labels once CaseHub/Qhorus are ready |
