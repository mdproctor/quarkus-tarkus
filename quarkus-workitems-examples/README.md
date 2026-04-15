# quarkus-tarkus-examples

Runnable scenario demonstrations for every ledger, audit, and lifecycle capability
of `quarkus-tarkus`. Each scenario runs in one HTTP call, logs a narrative to
stdout, and returns the full ledger trail as JSON.

## Capability Coverage

| Capability | S1 Expense | S2 Credit | S3 Moderation | S4 Queue |
|---|---|---|---|---|
| create / claim / start / complete | ✅ | ✅ | ✅ | ✅ |
| reject | | | ✅ | |
| delegate + causedByEntryId | | ✅ | | |
| release | | | | ✅ |
| suspend + resume | | ✅ | | |
| candidateGroups / work queues | | | | ✅ |
| inbox filtering | | | | ✅ |
| Hash chain (SHA-256) | ✅ | ✅ | ✅ | ✅ |
| decisionContext (GDPR Art. 22) | ✅ | ✅ | ✅ | ✅ |
| rationale + planRef | | ✅ | ✅ | |
| evidence capture | | | ✅ | |
| provenance (sourceEntity*) | | ✅ | ✅ | |
| peer attestations | | ✅ | ✅ | |
| actorType AGENT | | | ✅ | |
| actorType HUMAN | ✅ | ✅ | ✅ | ✅ |
| EigenTrust trust scores | | | | ✅ |
| trust score endpoint | | | | ✅ |

## Prerequisites

- Java 26: `export JAVA_HOME=$(/usr/libexec/java_home -v 26)`
- Maven (not the wrapper): `mvn`
- Build the parent first: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn install -DskipTests`

## Running

```bash
# Start in dev mode (H2 in-memory, auto-restart)
JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn quarkus:dev -pl quarkus-tarkus-examples
```

Quarkus starts on `http://localhost:8080`. All four scenarios are ready immediately.

## Scenario 1: Expense Approval

**What it demonstrates:** Baseline lifecycle (create → claim → start → complete),
automatic ledger recording, SHA-256 hash chain, decisionContext snapshots.

```bash
curl -s -X POST http://localhost:8080/examples/expense/run | jq .
```

**Key fields in the response:**

- `ledgerEntries[0].digest` — SHA-256 of entry 1; used as `previousHash` of entry 2
- `ledgerEntries[*].previousHash` — each entry chains to the prior digest
- `ledgerEntries[*].decisionContext` — JSON snapshot of WorkItem state at each transition

**Stdout while running:**
```
[SCENARIO] Step 1/4: finance-system Creates expense WorkItem for alice (priority=HIGH)
[SCENARIO] Step 2/4: alice claims WorkItem
[SCENARIO] Step 3/4: alice starts work
[SCENARIO] Step 4/4: alice completes — approved
```

## Scenario 2: Regulated Credit Decision

**What it demonstrates:** Provenance linking, suspend/resume, delegation with
`causedByEntryId`, rationale + planRef (GDPR Article 22 / EU AI Act Article 12
compliance), peer attestation (dual-control pattern), full 9-entry hash chain.

```bash
curl -s -X POST http://localhost:8080/examples/credit/run | jq .
```

**Key fields in the response:**

- `ledgerEntries[0].sourceEntityId/Type/System` — provenance from credit-engine
- Suspension entry: `eventType=WorkItemSuspended` with detail "Awaiting payslip documents"
- Delegation entry: `causedByEntryId` links to the resume entry that preceded it
- Completion entry: `rationale` and `planRef` (credit-policy-v2.1) for GDPR compliance
- Completion entry: `attestations[0].verdict=SOUND` from compliance-carol (dual-control)

## Scenario 3: AI Content Moderation

**What it demonstrates:** Evidence capture, `actorType=AGENT` for the AI creator,
`actorType=HUMAN` for the moderator, provenance from an AI system, agent attestation
on a human decision.

```bash
curl -s -X POST http://localhost:8080/examples/moderation/run | jq .
```

**Key fields in the response:**

- `ledgerEntries[0].actorType=AGENT` — created by `agent:content-ai`
- `ledgerEntries[0].evidence` — `{"flagReason":"hate-speech","confidence":0.73,"modelVersion":"mod-v3"}`
- `ledgerEntries[0].sourceEntitySystem=content-ai` — provenance
- Rejection entry: `rationale` = "Context review: satire, not hate speech"
- Rejection entry: `attestations[0].attestorType=AGENT` — compliance bot ENDORSED the override

**Actor type derivation:** Any `actorId` starting with `"agent:"` maps to `actorType=AGENT`.
Starting with `"system:"` maps to `SYSTEM`. All others are `HUMAN`.

## Scenario 4: Document Review Queue

**What it demonstrates:** `candidateGroups` work queue routing, inbox filtering,
release (un-claim without delegating), multiple actors building ledger history,
EigenTrust reputation scoring.

```bash
curl -s -X POST http://localhost:8080/examples/queue/run | jq .
```

**Key fields in the response:**

- `workItemIds` — three WorkItems created in `candidateGroups=["doc-reviewers"]`
- `allLedgerEntries` — all transitions across all three items (14 total)
- Release entry: `eventType=WorkItemReleased`, `actorId=reviewer-alice`
- `reviewerBobTrust.trustScore` — computed EigenTrust score for bob
- `reviewerAliceTrust.trustScore` — bob ≥ alice (more completions, no releases)

**Query a trust score directly:**
```bash
curl -s http://localhost:8080/tarkus/actors/reviewer-bob/trust | jq .
```

## Running All Four in Sequence

```bash
curl -s -X POST http://localhost:8080/examples/expense/run | jq '.scenario, (.ledgerEntries | length)'
curl -s -X POST http://localhost:8080/examples/credit/run | jq '.scenario, (.ledgerEntries | length)'
curl -s -X POST http://localhost:8080/examples/moderation/run | jq '.scenario, (.ledgerEntries | length)'
curl -s -X POST http://localhost:8080/examples/queue/run | jq '.scenario, (.allLedgerEntries | length)'
```

## Other Lifecycle Transitions

`cancel` and expiry/escalation are not covered by named scenarios but work via
the standard WorkItem REST API:

```bash
# Create and immediately cancel a WorkItem
ID=$(curl -s -X POST http://localhost:8080/tarkus/workitems \
  -H 'Content-Type: application/json' \
  -d '{"title":"Test cancel","createdBy":"demo"}' | jq -r '.id')
curl -s -X PUT "http://localhost:8080/tarkus/workitems/$ID/cancel?actor=admin" \
  -H 'Content-Type: application/json' \
  -d '{"reason":"No longer needed"}' | jq .
```

Expiry + escalation: set `quarkus.tarkus.default-expiry-hours=0` in
`application.properties` and wait for the scheduler. The ledger records
`WorkItemExpired`/`WorkItemEscalated` entries with `actorType=SYSTEM` (via the
`system:` prefix convention).

## Running the Tests

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl quarkus-tarkus-examples
```

Expected: 4 tests, 0 failures.
