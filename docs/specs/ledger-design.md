# Quarkus WorkItems — Ledger, Audit, and Provenance Design

> *This document covers the full accountability stack: from simple audit trails to
> causal provenance graphs, peer attestation, and EigenTrust reputation.*

---

## Design Principle: Complexity Must Not Leak

The ledger is an **optional, separate Maven module** — `quarkus-workitems-ledger`.

If you don't add it as a dependency, your application is identical to using
`quarkus-workitems` today. No extra tables, no extra config keys, no overhead. The
core extension doesn't know the ledger exists.

This is possible because Phase 4 already established the right seam:
`WorkItemService` fires `WorkItemLifecycleEvent` CDI events on every transition.
The ledger module is a CDI observer of those events. Observer with no implementation
= event fires into the void.

---

## Module Architecture

```
quarkus-workitems-parent
├── quarkus-workitems                  ← core (unchanged)
├── quarkus-workitems-deployment       ← core deployment
├── quarkus-workitems-testing          ← InMemory impls for unit tests
├── quarkus-workitems-flow             ← Quarkus-Flow CDI bridge
├── quarkus-workitems-ledger           ← NEW: fully optional ledger module
│   └── (no separate deployment needed — pure runtime CDI)
└── integration-tests               ← native image validation
```

**How the separation works at runtime:**

```
quarkus-workitems (core)                        quarkus-workitems-ledger (optional)
─────────────────────────────────────────    ─────────────────────────────────────────
WorkItem entity                              LedgerEntry entity
AuditEntry entity                            LedgerAttestation entity
WorkItemService                              ActorTrustScore entity
  └─ fires WorkItemLifecycleEvent ────────►  LedgerEventCapture (@Observes)
WorkItemResource (/workitems)           └─ writes LedgerEntry
WorkItemLifecycleEvent (+ 2 new fields)        └─ queries WorkItemRepo for snapshot
WorkItemsConfig (stays clean)                   LedgerResource (/workitems/.../ledger)
                                             LedgerConfig (@ConfigMapping)
                                             db/ledger-migration/ (own migration path)
```

If `quarkus-workitems-ledger` is absent: CDI events fire into the void. No ledger
tables. No config. No overhead. Zero core changes required.

---

## The Extension Point: WorkItemLifecycleEvent

`WorkItemLifecycleEvent` (Phase 4) is the clean seam. Two nullable fields added
to support richer context — useful to any observer, not just the ledger:

```java
public record WorkItemLifecycleEvent(
    String type,        // "io.quarkiverse.workitems.workitem.completed"
    String source,      // "/workitems/{id}"
    String subject,     // workItemId.toString()
    UUID workItemId,
    WorkItemStatus status,
    Instant occurredAt,
    String actor,
    String detail,      // existing — delegation target, rejection reason, etc.
    String rationale,   // NEW nullable — actor's stated basis for the decision
    String planRef      // NEW nullable — policy/procedure version that governed this
) {}
```

The ledger module reads `rationale` and `planRef` from the event. Non-ledger
observers ignore them. The core provides them when available (e.g., from a REST
request body field), null otherwise.

---

## sourceEntityRef: Separate Ledger Endpoint

Provenance information (which Flow workflow, CaseHub case, or Qhorus agent created
this WorkItem) is **not added to `WorkItemCreateRequest`** — that would leak ledger
concepts into the core REST API.

Instead, the creating system calls a ledger-specific endpoint after WorkItem creation:

```
POST /workitems/{id}/ledger/provenance
{
  "sourceEntityId":     "workflow-instance-abc123",
  "sourceEntityType":   "Flow:WorkflowInstance",
  "sourceEntitySystem": "quarkus-flow"
}
```

This endpoint is on `LedgerResource` in `quarkus-workitems-ledger`. If the module
isn't present, the endpoint doesn't exist. No impact on the core API.

---

## Database Migrations

`quarkus-workitems-ledger` registers its own Flyway migration path
(`db/ledger-migration/`) via a Quarkus `@BuildStep` in its deployment processor.
Core's `db/migration/` (V1__initial_schema.sql) is untouched.

If the ledger module is absent, the `ledger_entry`, `ledger_attestation`, and
`actor_trust_score` tables are never created.

---

## Configuration

`LedgerConfig` is a `@ConfigMapping(prefix = "quarkus.workitems.ledger")` interface
in `quarkus-workitems-ledger`. These config keys are invisible unless the module is
on the classpath.

| Property | Default | Meaning |
|---|---|---|
| `quarkus.workitems.ledger.enabled` | `true` | Master switch when module is present |
| `quarkus.workitems.ledger.hash-chain.enabled` | `true` | SHA-256 tamper-evidence chain |
| `quarkus.workitems.ledger.decision-context.enabled` | `true` | WorkItem snapshot at each transition |
| `quarkus.workitems.ledger.evidence.enabled` | `false` | Structured evidence capture (opt-in) |
| `quarkus.workitems.ledger.attestations.enabled` | `true` | Stamp/attestation REST endpoints |
| `quarkus.workitems.ledger.trust-score.enabled` | `false` | Nightly EigenTrust batch computation |
| `quarkus.workitems.ledger.trust-score.decay-half-life-days` | `90` | Score decay rate |
| `quarkus.workitems.ledger.trust-routing.enabled` | `false` | Trust-score-based routing |

**Rationale for defaults:** hash-chain and decision-context default ON — low
overhead, high compliance value (GDPR, EU AI Act). evidence, trust-score, and
trust-routing default OFF — require either caller cooperation or accumulated
history before being useful, and trust-routing changes WorkItem routing behaviour.

---

## The Six Capabilities

### 1. Command/Event Separation (Intent vs. Fact)

`LedgerEventCapture` observes the CDI event and writes a `LedgerEntry` with both
`commandType` ("ApproveWorkItem") and `eventType` ("WorkItemApproved"), plus
`rationale` from the event and `actorRole` derived from the event type.

**Always on when module is present and `ledger.enabled=true`.**

### 2. Decision Context Snapshot

When `decision-context.enabled=true`, `LedgerEventCapture` loads the full
`WorkItem` from `WorkItemRepository` at observation time (synchronous, same
transaction as the CDI event delivery) and snapshots its state as JSON into
`decisionContext`. This is the point-in-time record required by GDPR Article 22
and EU AI Act Article 12.

```json
{
  "status": "IN_PROGRESS",
  "priority": "HIGH",
  "assigneeId": "alice",
  "expiresAt": "2026-04-15T12:00:00Z",
  "auditEntriesCount": 3
}
```

**Configurable:** `quarkus.workitems.ledger.decision-context.enabled`

### 3. Plan Reference

`planRef` is taken from `WorkItemLifecycleEvent.planRef()` (new nullable field).
Set by calling systems when they know which policy governed the action. Null when
not provided — no overhead, no empty field writes beyond the nullable column.

**Always on when module is present. Simply null when not provided.**

### 4. Evidence as First-Class Object

When `evidence.enabled=true`, the REST endpoints for WorkItem lifecycle operations
accept an optional `evidence` JSON body field. The ledger stores it as structured
evidence alongside the `LedgerEntry`.

**Configurable — off by default.** Requires callers to supply structured evidence;
enabling without caller support produces null evidence fields.

### 5. Hash Chain (Tamper Evidence)

When `hash-chain.enabled=true`, each `LedgerEntry` carries:
- `previousHash` — the `digest` of the prior entry for this WorkItem
  (`"GENESIS"` for the first entry)
- `digest` — `SHA-256(previousHash + "|" + canonicalContent)`

Canonical content: `workItemId|seqNum|entryType|commandType|eventType|actorId|actorRole|planRef|occurredAt`

Certificate Transparency pattern — no blockchain, just a hash-chained append-only
table. An auditor recomputes digests and detects any tampering.

**Configurable.** Default on; disable only in development environments.

### 6. EigenTrust Reputation

When `trust-score.enabled=true`, a nightly `@Scheduled` job computes per-actor
trust scores from `LedgerEntry` outcomes using a simplified EigenTrust model:
localTrust = upheld/total, weighted by recency (exponential decay with configurable
half-life), propagated transitively through delegation chains.

Scores are stored in `ActorTrustScore`. When `trust-routing.enabled=true`,
`WorkItemService` (core) is not modified — instead, `LedgerEventCapture` posts
a routing suggestion via a CDI event that the consuming application can observe.
The core routing (`candidateGroups`) is unchanged.

**Both off by default.** Trust scores need accumulated history to be meaningful;
enabling on a new deployment produces unreliable early scores.

---

## Data Model

### LedgerEntry

```
ledger_entry
├── id                    UUID PK
├── work_item_id          UUID NOT NULL
├── sequence_number       INT NOT NULL
├── entry_type            VARCHAR(20) NOT NULL   — COMMAND | EVENT | ATTESTATION
├── command_type          VARCHAR(100)           — nullable
├── event_type            VARCHAR(100)           — nullable
├── actor_id              VARCHAR(255)
├── actor_type            VARCHAR(20)            — HUMAN | AGENT | SYSTEM
├── actor_role            VARCHAR(100)           — nullable
├── plan_ref              VARCHAR(500)           — nullable
├── rationale             TEXT                   — nullable
├── decision_context      TEXT                   — JSONB snapshot, nullable
├── evidence              TEXT                   — JSONB, nullable
├── caused_by_entry_id    UUID                   — FK → ledger_entry, nullable
├── correlation_id        VARCHAR(255)           — OTEL trace ID, nullable
├── source_entity_id      VARCHAR(255)           — nullable
├── source_entity_type    VARCHAR(255)           — nullable
├── source_entity_system  VARCHAR(100)           — nullable
├── previous_hash         VARCHAR(64)            — nullable (GENESIS for first)
├── digest                VARCHAR(64)            — nullable (when chain disabled)
└── occurred_at           TIMESTAMP NOT NULL
```

### LedgerAttestation

```
ledger_attestation
├── id                    UUID PK
├── ledger_entry_id       UUID NOT NULL FK → ledger_entry
├── work_item_id          UUID NOT NULL          — denormalized for queries
├── attestor_id           VARCHAR(255) NOT NULL
├── attestor_type         VARCHAR(20) NOT NULL   — HUMAN | AGENT
├── attestor_role         VARCHAR(100)           — nullable
├── verdict               VARCHAR(20) NOT NULL   — SOUND | FLAGGED | ENDORSED | CHALLENGED
├── evidence              TEXT                   — nullable
├── confidence            DOUBLE NOT NULL        — 0.0–1.0
└── occurred_at           TIMESTAMP NOT NULL
```

### ActorTrustScore

```
actor_trust_score
├── actor_id              VARCHAR(255) PK
├── actor_type            VARCHAR(20) NOT NULL
├── trust_score           DOUBLE NOT NULL        — 0.0–1.0
├── decision_count        INT NOT NULL DEFAULT 0
├── overturned_count      INT NOT NULL DEFAULT 0
├── appeal_count          INT NOT NULL DEFAULT 0
├── attestation_positive  INT NOT NULL DEFAULT 0
├── attestation_negative  INT NOT NULL DEFAULT 0
└── last_computed_at      TIMESTAMP NOT NULL
```

---

## REST API (in quarkus-workitems-ledger)

All endpoints are on `LedgerResource`. **None of these exist if the module is absent.**

| Method | Path | Description |
|---|---|---|
| `GET` | `/workitems/{id}/ledger` | All ledger entries with attestations |
| `POST` | `/workitems/{id}/ledger/provenance` | Set source entity reference |
| `POST` | `/workitems/{id}/ledger/{entryId}/attestations` | Post a stamp |
| `GET` | `/workitems/actors/{actorId}/trust` | Get actor trust score (when enabled) |

---

## Implementation Order

| Issue | What | Depends on |
|---|---|---|
| #41 | LedgerEntry entity + repository SPI + migration | — |
| #42 | LedgerEventCapture observer + LedgerConfig + WorkItemLifecycleEvent enrichment | #41 |
| #43 | LedgerAttestation entity + POST endpoint | #41 |
| #44 | GET /ledger endpoint + provenance endpoint + full tests | #41, #42, #43 |
| #45 | ActorTrustScore + EigenTrust batch | #42 |
| #46 | LedgerConfig @ConfigMapping | merged into #42 |
| #39 | ProvenanceLink typed graph | Future — after CaseHub/Qhorus ship |

---

## What the Core Does NOT Change

- `WorkItemService` — no ledger injection, no `if (ledgerConfig...)` guards
- `WorkItemCreateRequest` — no sourceEntityRef fields
- `WorkItemsConfig` — no ledger config section
- `AuditEntry` — unchanged, still written as today
- V1 migration — untouched
- All existing tests — unaffected

The only core change: `WorkItemLifecycleEvent` gains two nullable fields
(`rationale`, `planRef`) — backward compatible, ignored by any observer that
doesn't use them.

---

## ProvenanceLink (Future — Issue #39)

Once CaseHub and Qhorus integrations ship, the lightweight `sourceEntityRef` fields
on `LedgerEntry` can be promoted to a full `ProvenanceLink` typed-edge graph
modelling W3C PROV-O relationships (GENERATED_BY, USED, DERIVED_FROM, INFORMED_BY).
See issue #39. The `quarkus-workitems-ledger` module is the natural home for this.

---

## Research Basis

- W3C PROV-O — causal graph model, Plan/Role/qualified associations
- W3C Verifiable Credentials — evidence as first-class object, selective disclosure
- EigenTrust — transitive trust propagation in peer networks
- EU AI Act Articles 12, 19 — automatic logging, 6-month retention
- GDPR Article 22 / Recital 71 — right to explanation, point-in-time context
- Event sourcing — command/event separation, compensating events
- Gastown — stamps/attestation model, agent CVs as reputation
- Hyperledger Fabric — read-set capture, endorsement signatures
- OTel GenAI SemConv — `gen_ai.conversation.id` for agent tracing
- Certificate Transparency — hash chain without blockchain
