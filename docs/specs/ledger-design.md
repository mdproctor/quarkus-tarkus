# Quarkus Tarkus — Ledger, Audit, and Provenance Design

> *This document covers the full accountability stack: from simple audit trails to
> causal provenance graphs, peer attestation, and EigenTrust reputation. Each
> capability is independently configurable.*

---

## Why This Exists

Every BPM system records what happened. Tarkus records why, how confidently, under
what policy, with what information visible at the time, and whether peers validated
the decision. Together these answer questions no current human task system can:

- *Was this approval justified given what was known at the time?*
- *Which actors — human or AI agent — have the best track record on this category of decision?*
- *Prove that the AI did not make the final call on this regulated decision.*
- *Reconstruct the full causal chain from case creation to human sign-off.*

---

## What This Is Not

**AuditEntry** (existing) — a simple per-transition log: event, actor, timestamp.
Fast, always-on, sufficient for operational "who did what when" queries.

**LedgerEntry** (new) — a richer accountability record capturing intent, context,
evidence, causal links, and a tamper-evident hash chain. Optional capabilities
are individually configurable.

The two coexist. `AuditEntry` remains unchanged. `LedgerEntry` is additive.

---

## The Six Capabilities

### Capability 1: Command/Event Separation (Intent vs. Fact)

**What it captures that audit logs miss:** The actor's declared intent alongside
the system-recorded fact.

Every transition produces a `LedgerEntry` with both a `commandType` (the intent:
`ApproveWorkItem`) and an `eventType` (the fact: `WorkItemApproved`). The rationale
field carries the actor's stated basis — why they believed this was the right action.

```
commandType: "ApproveWorkItem"
eventType:   "WorkItemApproved"
rationale:   "All compliance criteria met; medical history reviewed; physician licensed"
actorRole:   "PrimaryReviewer"
planRef:     "MedicalReviewProtocol-2026-Q1"
```

**Configuration:** Always on when `quarkus.tarkus.ledger.enabled=true`.

---

### Capability 2: Decision Context Snapshot (Read-Set Capture)

**What it captures:** A point-in-time snapshot of the information that was visible
to the decision-maker at the moment of the transition.

Required by GDPR Article 22 (right to explanation), EU AI Act Article 12 (automatic
logging of inputs), and MiFID II. No current BPM system does this.

```json
{
  "workItemStatus": "IN_PROGRESS",
  "workItemPriority": "HIGH",
  "assigneeId": "alice",
  "expiresAt": "2026-04-15T12:00:00Z",
  "auditEntriesCount": 3,
  "lastAuditEvent": "STARTED",
  "agentRecommendation": { "score": 0.87, "verdict": "approve" }
}
```

The snapshot is captured at call time, not reconstructed later. Three months after
an approval, you can show exactly what was displayed to the reviewer.

**Configuration:** `quarkus.tarkus.ledger.decision-context.enabled` (default: `true`).
Disable for high-throughput systems where snapshot storage is prohibitive.

---

### Capability 3: Plan Reference (Which Procedure Governed This?)

**What it captures:** Which policy, procedure, or workflow version governed the
action — not just who did it.

```
planRef: "EscalationPolicy-v3"
planRef: "MedicalReviewProtocol-2026-Q1"
planRef: "DefaultApprovalFlow"
```

Enables compliance queries like: *"Every WorkItem processed under
MedicalReviewProtocol before Q2 — were they all handled correctly?"*

`planRef` is nullable. When set, it comes from:
- The calling system (Flow, CaseHub) via `WorkItemCreateRequest`
- The escalation policy that triggered the transition
- Manually set by the actor's client

**Configuration:** Always on when `quarkus.tarkus.ledger.enabled=true`. Simply null
when not provided.

---

### Capability 4: Evidence as First-Class Object

**What it captures:** Structured evidence supporting the decision — what was
checked, what criteria were met, what documents were reviewed.

Inspired by W3C Verifiable Credentials' `evidence` property. An approval with
evidence is audit-defensible; a bare approval is a timestamp.

```json
{
  "criteriaRef": "financial-approval-criteria-v2",
  "itemsReviewed": ["invoice-221", "purchase-order-88", "budget-auth-Q1"],
  "checksPerformed": ["amount-within-budget", "vendor-approved", "category-allowed"],
  "externalRef": "approval-system-ref-9921"
}
```

**Configuration:** `quarkus.tarkus.ledger.evidence.enabled` (default: `false`).
Opt-in because callers must actively provide structured evidence data — enabling
it without caller support produces empty evidence fields.

---

### Capability 5: Hash Chain (Tamper Evidence)

**What it captures:** Cryptographic proof that the ledger has not been tampered
with after the fact. No blockchain required.

Each entry's `digest` is `SHA-256(previousHash + "|" + canonicalContent)`. The
first entry uses `"GENESIS"` as `previousHash`. Any entry modification, insertion,
or deletion breaks the chain verification.

This is the same pattern used by Certificate Transparency logs and git's object
model. A standard Postgres append-only table with a `@PrePersist` hash computation
provides the same tamper-evidence as a blockchain at 1% of the operational cost.

**Verification:** A scheduled job can re-verify the chain on demand, or an auditor
can recompute digests from the canonical content fields and compare.

```
entry[0]: previousHash=GENESIS, digest=sha256("GENESIS|workItemId|1|EVENT|...")
entry[1]: previousHash=entry[0].digest, digest=sha256(entry[0].digest+"|workItemId|2|EVENT|...")
entry[2]: previousHash=entry[1].digest, digest=sha256(entry[1].digest+"|workItemId|3|EVENT|...")
```

**Configuration:** `quarkus.tarkus.ledger.hash-chain.enabled` (default: `true`).
Disable only for development environments where audit tamper-evidence is not
required and the computation overhead (negligible) is still undesired.

---

### Capability 6: EigenTrust Reputation (Actor Trust Scores)

**What it captures:** How reliable each actor — human or AI agent — has proven
to be over time, computed from decision outcomes and propagated transitively
through delegation chains.

Inspired by the EigenTrust algorithm (used in peer-to-peer systems). Core idea:
- **Local trust**: `localTrust(actor) = upheld / total`, weighted by recency
- **Transitivity**: delegating to a high-trust actor reflects on the delegator
- **Decay**: recent outcomes weighted more than historical ones
- **Convergence**: nightly batch computation produces stable global scores

```
alice:     trustScore=0.94, decisions=47, overturned=3, appeals=1
agent-007: trustScore=0.89, decisions=312, overturned=35, appeals=12
```

**Use in routing:** When `candidateGroups` contains multiple eligible actors,
WorkItemService can sort by trust score. Configurable separately from computation.

**Use in attestation weighting:** Stamps from high-trust actors carry more weight
in aggregate confidence calculations.

**Configuration:**
- `quarkus.tarkus.ledger.trust-score.enabled` (default: `false`) — nightly batch computation
- `quarkus.tarkus.ledger.trust-routing.enabled` (default: `false`) — use scores in routing

Both default off because: computation requires ledger history (which takes time
to accumulate), routing behavior changes are significant, and early scores based
on small samples are unreliable.

---

## Data Model

### LedgerEntry

```
ledger_entry
├── id                    UUID PK
├── work_item_id          UUID FK → work_item
├── sequence_number       INT (monotonic per WorkItem, managed by service)
│
├── entry_type            VARCHAR(20)  — COMMAND | EVENT | ATTESTATION
├── command_type          VARCHAR(100) — "ApproveWorkItem" etc. (nullable)
├── event_type            VARCHAR(100) — "WorkItemApproved" etc. (nullable)
│
├── actor_id              VARCHAR(255)
├── actor_type            VARCHAR(20)  — HUMAN | AGENT | SYSTEM
├── actor_role            VARCHAR(100) — "PrimaryReviewer", "Delegator" etc. (nullable)
│
├── plan_ref              VARCHAR(500) — policy/procedure version (nullable)
├── rationale             TEXT         — actor's stated basis (nullable)
├── decision_context      TEXT         — JSONB snapshot at transition time (nullable)
├── evidence              TEXT         — JSONB structured evidence (nullable)
│
├── caused_by_entry_id    UUID         — FK → ledger_entry (nullable)
├── correlation_id        VARCHAR(255) — OTEL trace ID (nullable)
│
├── source_entity_id      VARCHAR(255) — upstream trigger ID (nullable)
├── source_entity_type    VARCHAR(255) — "CaseHub:Case", "Flow:WorkflowInstance" (nullable)
├── source_entity_system  VARCHAR(100) — "casehub", "quarkus-flow", "qhorus" (nullable)
│
├── previous_hash         VARCHAR(64)  — SHA-256 of prior entry (nullable if first)
├── digest                VARCHAR(64)  — SHA-256 chain digest (nullable if hash chain disabled)
│
└── occurred_at           TIMESTAMP NOT NULL
```

### LedgerAttestation (Stamps)

```
ledger_attestation
├── id                    UUID PK
├── ledger_entry_id       UUID FK → ledger_entry
├── work_item_id          UUID        — denormalized for query convenience
├── attestor_id           VARCHAR(255)
├── attestor_type         VARCHAR(20)  — HUMAN | AGENT
├── attestor_role         VARCHAR(100) — (nullable)
├── verdict               VARCHAR(20)  — SOUND | FLAGGED | ENDORSED | CHALLENGED
├── evidence              TEXT         — what the attestor reviewed (nullable)
├── confidence            DOUBLE       — 0.0–1.0
└── occurred_at           TIMESTAMP NOT NULL
```

### ActorTrustScore

```
actor_trust_score
├── actor_id              VARCHAR(255) PK
├── actor_type            VARCHAR(20)
├── trust_score           DOUBLE       — 0.0–1.0
├── decision_count        INT
├── overturned_count      INT
├── appeal_count          INT
├── attestation_positive  INT          — SOUND/ENDORSED attestations received
├── attestation_negative  INT          — FLAGGED/CHALLENGED attestations received
└── last_computed_at      TIMESTAMP
```

---

## REST API Extensions

### GET /tarkus/workitems/{id}/ledger

Returns `List<LedgerEntryWithAttestationsResponse>` — all ledger entries for this
WorkItem in sequence order, each with embedded attestations.

### POST /tarkus/workitems/{id}/ledger/{entryId}/attestations

Post a stamp on a ledger entry. Body: `{attestorId, attestorType, verdict, evidence, confidence}`.
Returns 201 + `LedgerAttestationResponse`.

### GET /tarkus/actors/{actorId}/trust

Returns `ActorTrustScoreResponse` when trust scoring is enabled. 404 when disabled
or no score computed yet.

---

## Configuration Reference

All under `quarkus.tarkus.ledger`:

| Property | Default | Meaning |
|---|---|---|
| `enabled` | `true` | Master switch — disabling skips all ledger writes |
| `hash-chain.enabled` | `true` | SHA-256 tamper-evidence chain |
| `decision-context.enabled` | `true` | Snapshot of WorkItem state at each transition |
| `evidence.enabled` | `false` | Structured evidence capture (opt-in — callers must supply) |
| `attestations.enabled` | `true` | Stamp/attestation REST endpoints |
| `trust-score.enabled` | `false` | Nightly EigenTrust batch computation |
| `trust-score.decay-half-life-days` | `90` | Exponential decay for historical outcomes |
| `trust-routing.enabled` | `false` | Use trust scores in candidateGroup routing |

**Design principle:** Default on for capabilities with low overhead and high
compliance value (hash chain, decision context). Default off for capabilities
that change behavior (routing) or require caller cooperation (evidence) or
accumulate significant history before being useful (trust scores).

---

## Optionality Implementation

WorkItemService uses a single guard pattern:

```java
if (ledgerConfig.enabled()) {
    LedgerEntry entry = ledgerEntryBuilder
        .command(commandType, eventType, actorId, actorType, actorRole)
        .rationale(rationale)
        .decisionContext(ledgerConfig.decisionContext().enabled()
            ? captureContext(workItem) : null)
        .evidence(ledgerConfig.evidence().enabled() ? evidence : null)
        .planRef(planRef)
        .sourceEntity(sourceEntityId, sourceEntityType, sourceEntitySystem)
        .build(workItem, ledgerRepo);     // handles hash chain and sequenceNumber

    ledgerRepo.save(entry);
}
```

Each optional field is null when its capability is disabled — no separate code
paths, just nullable fields.

---

## Provenance Link (Future — Issue #39)

Once CaseHub and Qhorus integrations ship and have been populating the
`sourceEntityRef` fields for 3+ months, the lightweight three-field model
can be promoted to a full `ProvenanceLink` typed-edge graph modelling PROV-O
relationships (GENERATED_BY, USED, DERIVED_FROM, INFORMED_BY). See issue #39.

---

## Implementation Phases

| Issue | Capability | Depends On |
|---|---|---|
| #46 | LedgerConfig @ConfigMapping | — |
| #41 | LedgerEntry entity + SPI + migration | #46 |
| #42 | WorkItemService integration | #41 |
| #43 | LedgerAttestation entity + endpoint | #41 |
| #44 | REST endpoints + full tests | #41, #42, #43 |
| #45 | ActorTrustScore + EigenTrust batch | #42 |
| #39 | ProvenanceLink typed graph | CaseHub + Qhorus integrations |

---

## Research Basis

Design informed by:
- W3C PROV-O (`wasGeneratedBy`, `used`, `wasDerivedFrom`, `hadPlan`, `hadRole`)
- W3C Verifiable Credentials (`evidence`, `credentialStatus`, selective disclosure)
- EigenTrust algorithm (transitive trust propagation in P2P networks)
- EU AI Act Articles 12, 19 (automatic logging, 6-month retention)
- GDPR Article 22 / Recital 71 (right to explanation, point-in-time context)
- Event sourcing patterns (command vs. event separation, compensating events)
- Gastown (`stamps`, propulsion principle, agent CVs as reputation)
- Hyperledger Fabric (read-set capture, endorsement signatures)
- LangSmith / OTel GenAI SemConv (OTEL `gen_ai.conversation.id` for agent tracing)
- Certificate Transparency (hash chain without blockchain)
