# Quarkus WorkItems — Claude Code Project Guide

## Project Type

type: java

**Stack:** Java 21 (on Java 26 JVM), Quarkus 3.32.2, GraalVM 25 (native image target)

---

## What This Project Is

Quarkus WorkItems is a standalone Quarkiverse extension providing **human-scale WorkItem lifecycle management**. It gives any Quarkus application a human task inbox with expiry, delegation, escalation, priority, and audit trail — usable independently or with optional integrations for Quarkus-Flow, CaseHub, and Qhorus.

**The core concept — WorkItem (not Task):**
A `WorkItem` is a unit of work requiring human attention or judgment. It is deliberately NOT called `Task` because:
- The CNCF Serverless Workflow SDK (used by Quarkus-Flow) has its own `Task` class (`io.serverlessworkflow.api.types.Task`) — a machine-executed workflow step
- CaseHub has its own `Task` class — a CMMN-style case work unit
Using `WorkItem` avoids naming conflicts and accurately describes what WorkItems manages: work that waits for a person.

**See the full glossary:** `docs/DESIGN.md` § Glossary

---

## Quarkiverse Naming

| Element | Value |
|---|---|
| GitHub repo | `mdproctor/quarkus-workitems` (→ `quarkiverse/quarkus-workitems` when submitted) |
| groupId | `io.quarkiverse.workitems` |
| Parent artifactId | `quarkus-workitems-parent` |
| Runtime artifactId | `quarkus-workitems` |
| Deployment artifactId | `quarkus-workitems-deployment` |
| Root Java package | `io.quarkiverse.workitems` |
| Runtime subpackage | `io.quarkiverse.workitems.runtime` |
| Deployment subpackage | `io.quarkiverse.workitems.deployment` |
| Config prefix | `quarkus.workitems` |
| Feature name | `workitems` |

---

## Ecosystem Context

WorkItems is part of the Quarkus Native AI Agent Ecosystem:

```
CaseHub (case orchestration)   Quarkus-Flow (workflow execution)   Qhorus (agent mesh)
         │                              │                               │
         └──────────────────────────────┼───────────────────────────────┘
                                        │
                              Quarkus WorkItems (WorkItem inbox)
                                        │
                              quarkus-workitems-casehub   (optional adapter)
                              quarkus-workitems-flow      (optional adapter)
                              quarkus-workitems-qhorus    (optional adapter)
```

WorkItems has **no dependency on CaseHub, Quarkus-Flow, or Qhorus** — it is the independent human task layer. The integration modules (future) depend on WorkItems, not vice versa.

**Related projects (read only, for context):**
- `~/claude/quarkus-qhorus` — agent communication mesh (Qhorus integration target)
- `~/claude/casehub` — case orchestration engine (CaseHub integration target)
- `~/dev/quarkus-flow` — workflow engine (Quarkus-Flow integration target; uses CNCF Serverless Workflow SDK)
- `~/claude/claudony` — integration layer; will surface WorkItems inbox in its dashboard

---

## Project Structure

```
quarkus-workitems/
├── runtime/                               — Extension runtime module
│   └── src/main/java/io/quarkiverse/workitems/runtime/
│       ├── config/WorkItemsConfig.java       — @ConfigMapping(prefix = "quarkus.workitems")
│       ├── model/
│       │   ├── WorkItem.java              — PanacheEntity (the core concept)
│       │   ├── WorkItemStatus.java        — enum: PENDING|ASSIGNED|IN_PROGRESS|...
│       │   ├── WorkItemPriority.java      — enum: LOW|NORMAL|HIGH|CRITICAL
│       │   └── AuditEntry.java            — PanacheEntity (append-only audit log)
│       ├── repository/
│       │   ├── WorkItemStore.java         — SPI: put, get, scan(WorkItemQuery), scanAll
│       │   ├── WorkItemQuery.java         — query value object: inbox(), expired(), claimExpired(), byLabelPattern(), all()
│       │   ├── AuditEntryStore.java       — SPI: append, findByWorkItemId
│       │   └── jpa/
│       │       ├── JpaWorkItemStore.java         — default Panache impl (@ApplicationScoped)
│       │       └── JpaAuditEntryStore.java        — default Panache impl (@ApplicationScoped)
│       ├── service/
│       │   ├── WorkItemService.java       — lifecycle management, expiry, delegation
│       │   └── EscalationPolicy.java      — SPI interface for escalation strategies
│       └── api/
│           └── WorkItemResource.java      — REST API at /workitems
├── deployment/                            — Extension deployment (build-time) module
│   └── src/main/java/io/quarkiverse/workitems/deployment/
│       └── WorkItemsProcessor.java           — @BuildStep: FeatureBuildItem
├── testing/                               — Test utilities module (quarkus-workitems-testing)
│   └── src/main/java/io/quarkiverse/workitems/testing/
│       ├── InMemoryWorkItemStore.java         — ConcurrentHashMap-backed, no datasource needed
│       └── InMemoryAuditEntryStore.java       — list-backed
├── docs/
│   ├── DESIGN.md                          — Implementation-tracking design document
│   └── specs/
│       └── 2026-04-14-tarkus-design.md   — Primary design specification
└── HANDOFF.md                             — Session context for resumption
```

**Integration modules (built):**
- `workitems-flow/` — Quarkus-Flow CDI bridge (`HumanTaskFlowBridge`, `PendingWorkItemRegistry`, `WorkItemFlowEventListener`)
- `quarkus-workitems-ledger/` — optional accountability module (command/event ledger, hash chain, attestation, EigenTrust)
- `quarkus-workitems-queues/` — optional label-based queue module (`WorkItemFilter`, `FilterChain`, `QueueView`, `WorkItemQueueState`)
  - `api/`: `FilterResource` (/filters), `QueueResource` (/queues), `QueueStateResource` (/workitems/{id}/relinquishable)
  - `model/`: `FilterScope`, `FilterAction`, `WorkItemFilter`, `FilterChain`, `QueueView`, `WorkItemQueueState`
  - `service/`: `WorkItemExpressionEvaluator` SPI, `ExpressionDescriptor`, `JexlConditionEvaluator`, `JqConditionEvaluator`, `WorkItemFilterBean`, `FilterEngine`, `FilterEngineImpl`, `FilterEvaluationObserver`
- `quarkus-workitems-examples/` — runnable scenario demos; 4 `@QuarkusTest` scenarios covering every ledger/audit capability, each runs via `POST /examples/{name}/run`
- `integration-tests/` — `@QuarkusIntegrationTest` suite and native image validation (19 tests, 0.084s native startup)

**Future integration modules (not yet scaffolded):**
- `workitems-casehub/` — CaseHub `WorkerRegistry` adapter (blocked: CaseHub not yet complete)
- `workitems-qhorus/` — Qhorus MCP tools (`request_approval`, `check_approval`, `wait_for_approval`) (blocked: Qhorus not yet complete)
- `workitems-mongodb/` — MongoDB-backed `WorkItemStore`
- `workitems-redis/` — Redis-backed `WorkItemStore`

---

## Build and Test

```bash
# Build all modules
JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn clean install

# Run tests (runtime module)
JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl runtime

# Run tests (ledger module)
JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl quarkus-workitems-ledger

# Run tests (queues module)
JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl quarkus-workitems-queues

# Run tests (examples module)
JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl quarkus-workitems-examples

# Run specific test
JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -Dtest=ClassName -pl runtime

# Black-box integration tests (JVM mode)
JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn verify -pl integration-tests

# Native image integration tests (requires GraalVM 25)
JAVA_HOME=/Library/Java/JavaVirtualMachines/graalvm-25.jdk/Contents/Home \
  mvn verify -Pnative -pl integration-tests
```

**Use `mvn` not `./mvnw`** — maven wrapper not configured on this machine.

**`quarkus-ledger` prerequisite:** `quarkus-workitems-ledger` depends on `io.quarkiverse.ledger:quarkus-ledger:1.0.0-SNAPSHOT` — a sibling project at `~/claude/quarkus-ledger/`. If the build fails with "Could not find artifact", install it first:
```bash
JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn install -DskipTests -f ~/claude/quarkus-ledger/pom.xml
```

**Quarkiverse format check:** CI runs `mvn -Dno-format` to skip the enforced formatter. Run `mvn` locally to apply formatting.

**Known Quarkiverse gotchas (from quarkus-qhorus experience):**
- `quarkus-extension-processor` requires **Javadoc on every method** in `@ConfigMapping` interfaces, including group accessors — missing one causes a compile-time error
- The `extension-descriptor` goal validates that the deployment POM declares **all transitive deployment JARs** — run `mvn install -DskipTests` first after modifying the deployment POM
- `key` is a reserved word in H2 — avoid it as a column name in Flyway migrations
- `@QuarkusIntegrationTest` must live in a **separate module** from the extension runtime — the `quarkus-maven-plugin` build goal requires a configured datasource at augmentation time; extensions intentionally omit datasource config (use the `integration-tests/` module)
- `@Scheduled` intervals require `${property}s` syntax (MicroProfile Config), **not** `{property}s` — bare braces are silently ignored at augmentation time, causing `DateTimeParseException` at native startup
- Panache `find()` short-form WHERE clause must use **bare field names** (`assigneeId = :x`), not alias-prefixed names (`wi.assigneeId = :x`) — the alias is internal to Panache and not exposed in the condition string
- `quarkus.http.test-port=0` in test `application.properties` — add when a module has multiple `@QuarkusTest` classes; prevents intermittent `TIME_WAIT` port conflicts when Quarkus restarts between test classes
- `@TestTransaction` + REST assertions don't mix — a `@Transactional` CDI method called from within `@TestTransaction` joins the test transaction; subsequent HTTP calls run in their own transaction and cannot see the uncommitted data (returns 404). Remove `@TestTransaction` from test classes that mix direct service calls with REST Assured assertions
- If `deployment/pom.xml` declares `X-deployment` as a dependency, `runtime/pom.xml` **must** declare `X` (the corresponding runtime artifact) — the `extension-descriptor` goal enforces this pairing and fails with a misleading "Could not find artifact" error pointing at the runtime module. If `WorkItemsProcessor` doesn't use anything from `X-deployment`, remove it rather than adding an unnecessary runtime dependency.

---

## Java and GraalVM on This Machine

```bash
# Java 26 (Oracle, system default) — use for dev and tests
JAVA_HOME=$(/usr/libexec/java_home -v 26)

# GraalVM 25 — use for native image builds only
JAVA_HOME=/Library/Java/JavaVirtualMachines/graalvm-25.jdk/Contents/Home
```

---

## Design Document

`docs/specs/2026-04-14-tarkus-design.md` is the primary design specification.
`docs/DESIGN.md` is the implementation-tracking document (updated as phases complete).

---

## Work Tracking

**Issue tracking:** enabled
**GitHub repo:** mdproctor/quarkus-workitems

**Active epics** — priority order for market leadership:

| Priority | # | Epic | Status | First child |
|---|---|---|---|---|
| 1 | #98 | Form Schema — payload/resolution JSON Schema | **active** | #107 entity + CRUD |
| 2 | #99 | Audit History Query API — cross-WorkItem search | **active** | #109 GET /audit |
| 3 | #100 | AI-Native Features — confidence gating, semantic routing | **active** | confidence-gated routing |
| 4 | #101 | Business-Hours Deadlines — SLA in working hours | **active** | BusinessCalendar SPI |
| 5 | #102 | Workload-Aware Routing — least-loaded assignment | **active** | WorkItemRouter SPI |
| 6 | #103 | Notifications — Slack/Teams/email/webhook on lifecycle events | **active** | quarkus-workitems-notifications module |
| 7 | #104 | SLA Compliance Reporting — breach rates, actor performance | **active** | GET /workitems/reports/sla-breaches |
| 8 | #105 | Subprocess Spawning — template-driven child WorkItems | **active** | WorkItemSpawnRule entity |
| 9 | #106 | Multi-Instance Tasks — M-of-N parallel completion | **active** | MultiInstanceConfig on template |
| — | #92 | Distributed WorkItems — clustering + federation | future | #93 (SSE) implementable now |
| — | #79 | External System Integrations | blocked | CaseHub/Qhorus not stable |
| — | #39 | ProvenanceLink (PROV-O causal graph) | blocked | Awaiting #79 |
| ✅ | #77,78,80,81 | Collaboration, Queue Intelligence, Storage, Platform | complete | — |

**Automatic behaviours (Claude follows these at all times in this project):**
- **Before implementation begins** — check if an active issue exists. If not, run issue-workflow Phase 1 before writing any code. Create a child issue under the matching epic above.
- **Before any commit** — run issue-workflow Phase 3 to confirm issue linkage.
- **All commits should reference an issue** — `Refs #N` (ongoing) or `Closes #N` (done). Also reference the parent epic: `Refs #77` etc.
- **Code review fix commits** — when committing fixes found during a code review, create or reuse an issue for that review work **before** committing. Use `Refs #N` on the relevant epic even if it is already closed.
- **New feature requests** — assess which epic it belongs to before creating the issue. If none fits, propose a new epic first.
