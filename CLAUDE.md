# CaseHub Work — Claude Code Project Guide

## Platform Context

This repo is one component of the casehubio multi-repo platform. **Before implementing anything — any feature, SPI, data model, or abstraction — run the Platform Coherence Protocol.**

The protocol asks: Does this already exist elsewhere? Is this the right repo for it? Does this create a consolidation opportunity? Is this consistent with how the platform handles the same concern in other repos?

**Platform architecture (fetch before any implementation decision):**
```
https://raw.githubusercontent.com/casehubio/parent/main/docs/PLATFORM.md
```

**This repo's deep-dive:**
```
https://raw.githubusercontent.com/casehubio/parent/main/docs/repos/casehub-work.md
```

**Other repo deep-dives** (fetch the relevant ones when your implementation touches their domain):
- casehub-ledger: `https://raw.githubusercontent.com/casehubio/parent/main/docs/repos/casehub-ledger.md`
- casehub-qhorus: `https://raw.githubusercontent.com/casehubio/parent/main/docs/repos/casehub-qhorus.md`
- casehub-engine: `https://raw.githubusercontent.com/casehubio/parent/main/docs/repos/casehub-engine.md`
- claudony: `https://raw.githubusercontent.com/casehubio/parent/main/docs/repos/claudony.md`
- casehub-connectors: `https://raw.githubusercontent.com/casehubio/parent/main/docs/repos/casehub-connectors.md`

---

## Project Type

type: java

**Stack:** Java 21 (on Java 26 JVM), Quarkus 3.32.2, GraalVM 25 (native image target)

---

## What This Project Is

CaseHub Work is a **CaseHub platform module** providing **human-scale WorkItem lifecycle management**. It gives any Quarkus application a human task inbox with expiry, delegation, escalation, priority, and audit trail — usable independently or with optional integrations for Quarkus-Flow, CaseHub, and Qhorus. It is hosted under the CaseHub organisation (`casehubio/work`), not submitted to Quarkiverse.

**The core concept — WorkItem (not Task):**
A `WorkItem` is a unit of work requiring human attention or judgment. It is deliberately NOT called `Task` because:
- The CNCF Serverless Workflow SDK (used by Quarkus-Flow) has its own `Task` class (`io.serverlessworkflow.api.types.Task`) — a machine-executed workflow step
- CaseHub has its own `Task` class — a CMMN-style case work unit
Using `WorkItem` avoids naming conflicts and accurately describes what WorkItems manages: work that waits for a person.

**See the full glossary:** `docs/DESIGN.md` § Glossary

---

## Naming

| Element | Value |
|---|---|
| GitHub repo | `casehubio/work` |
| groupId | `io.casehub` |
| Parent artifactId | `casehub-work-parent` |
| Runtime artifactId | `casehub-work` |
| Deployment artifactId | `casehub-work-deployment` |
| Root Java package | `io.casehub.work` |
| Runtime subpackage | `io.casehub.work.runtime` |
| Deployment subpackage | `io.casehub.work.deployment` |
| Config prefix | `casehub.work` |
| Feature name | `workitems` |
| Version | `0.2-SNAPSHOT` (published to GitHub Packages under casehubio org) |

---

## Ecosystem Context

WorkItems is part of the Quarkus Native AI Agent Ecosystem:

```
CaseHub (case orchestration)   Quarkus-Flow (workflow execution)   Qhorus (agent mesh)
         │                              │                               │
         └──────────────────────────────┼───────────────────────────────┘
                                        │
                              CaseHub Work (WorkItem inbox)
                                        │
                              casehub-work-casehub   (optional adapter)
                              casehub-work-flow      (optional adapter)
                              casehub-work-qhorus    (optional adapter)
```

WorkItems has **no dependency on CaseHub, Quarkus-Flow, or Qhorus** — it is the independent human task layer. The integration modules (future) depend on WorkItems, not vice versa.

**Related projects (read only, for context):**
- `~/claude/casehub/qhorus` — agent communication mesh (Qhorus integration target)
- `~/claude/casehub/engine` — real CaseHub engine (CMMN + blackboard; **not** `~/claude/casehub-poc` which is the retiring POC)
- `~/dev/quarkus-flow` — workflow engine (Quarkus-Flow integration target; uses CNCF Serverless Workflow SDK)
- `~/claude/casehub/claudony` — integration layer; will surface WorkItems inbox in its dashboard

---

## Project Structure

```
casehub-work/
├── casehub-work-api/                      — Pure-Java SPI module (groupId io.casehub)
│   └── src/main/java/io/casehub/work/api/
│       ├── WorkerCandidate.java           — candidate assignee value object
│       ├── SelectionContext.java          — context passed to WorkerSelectionStrategy (workItemId, title, description, category, requiredCapabilities, candidateUsers, candidateGroups)
│       ├── AssignmentDecision.java        — result from WorkerSelectionStrategy
│       ├── AssignmentTrigger.java         — enum: CREATED|CLAIM_EXPIRED|MANUAL
│       ├── WorkerSelectionStrategy.java   — SPI: select(SelectionContext)
│       ├── WorkerRegistry.java            — SPI: candidates for a work unit
│       ├── WorkEventType.java             — enum: CREATED|ASSIGNED|EXPIRED|CLAIM_EXPIRED|SPAWNED|...
│       ├── WorkLifecycleEvent.java        — base lifecycle event (source, eventType, sourceUri)
│       ├── WorkloadProvider.java          — SPI: active workload count per worker
│       ├── EscalationPolicy.java          — SPI: escalate(WorkLifecycleEvent)
│       ├── SkillProfile.java              — record: narrative + attributes
│       ├── SkillProfileProvider.java      — SPI: getProfile(workerId, capabilities)
│       ├── SkillMatcher.java              — SPI: score(SkillProfile, SelectionContext)
│       ├── SpawnPort.java                 — SPI: spawn(SpawnRequest), cancelGroup(UUID, boolean)
│       ├── SpawnRequest.java              — record: parentId, idempotencyKey, children
│       ├── ChildSpec.java                 — record: templateId, callerRef, overrides
│       ├── SpawnResult.java               — record: groupId, children, created
│       └── SpawnedChild.java              — record: workItemId, callerRef
├── casehub-work-core/                     — Jandex library module (groupId io.casehub)
│   └── src/main/java/io/casehub/work/core/
│       ├── strategy/
│       │   ├── WorkBroker.java            — dispatches assignment via WorkerSelectionStrategy
│       │   ├── LeastLoadedStrategy.java   — assigns to worker with fewest open items
│       │   ├── ClaimFirstStrategy.java    — first-claim-wins strategy
│       │   └── NoOpWorkerRegistry.java    — no-op registry (no candidates returned)
│       └── policy/                        — claim SLA policies (ContinuationPolicy, FreshClockPolicy, etc.)
│   Note: no JPA entities, no REST resources — pure CDI + casehub-work-api. CaseHub depends on this directly.
├── runtime/                               — Extension runtime module
│   └── src/main/java/io/casehub/work/runtime/
│       ├── action/
│       │   ├── ApplyLabelAction.java      — FilterAction: apply label to WorkItem
│       │   ├── OverrideCandidateGroupsAction.java — FilterAction: replace candidate groups
│       │   └── SetPriorityAction.java     — FilterAction: set WorkItem priority
│       ├── config/WorkItemsConfig.java    — @ConfigMapping(prefix = "casehub.work")
│       ├── event/
│       │   ├── WorkItemContextBuilder.java — toMap(WorkItem) for JEXL context maps
│       │   ├── WorkItemEventBroadcaster.java — fires WorkItemLifecycleEvent via CDI
│       │   └── WorkItemLifecycleEvent.java — extends WorkLifecycleEvent; source() returns Object (the WorkItem)
│       ├── filter/                        — filter engine (moved from casehub-work-core in #133)
│       │   ├── FilterAction.java          — SPI: apply(Object workUnit, FilterDefinition)
│       │   ├── FilterDefinition.java      — filter rule definition value object
│       │   ├── FilterEvent.java           — event fired after filter evaluation
│       │   ├── ActionDescriptor.java      — registry entry for a FilterAction
│       │   ├── FilterRegistryEngine.java  — observes WorkLifecycleEvent, runs filters
│       │   ├── FilterRule.java            — persistent filter rule entity
│       │   ├── FilterRuleResource.java    — REST API at /filter-rules
│       │   ├── JexlConditionEvaluator.java — JEXL expression evaluator
│       │   ├── PermanentFilterRegistry.java — CDI-discovered static FilterAction registry
│       │   └── DynamicFilterRegistry.java — runtime-editable filter rule registry
│       ├── model/
│       │   ├── WorkItem.java              — PanacheEntity (the core concept); callerRef field for spawn routing
│       │   ├── WorkItemStatus.java        — enum: PENDING|ASSIGNED|IN_PROGRESS|...
│       │   ├── WorkItemPriority.java      — enum: LOW|NORMAL|HIGH|CRITICAL
│       │   ├── WorkItemSpawnGroup.java    — spawn batch tracking (idempotency + membership)
│       │   └── AuditEntry.java            — PanacheEntity (append-only audit log)
│       ├── repository/
│       │   ├── WorkItemStore.java         — SPI: put, get, scan(WorkItemQuery), scanAll
│       │   ├── WorkItemQuery.java         — query value object: inbox(), expired(), claimExpired(), byLabelPattern(), all()
│       │   ├── AuditEntryStore.java       — SPI: append, findByWorkItemId
│       │   └── jpa/
│       │       ├── JpaWorkItemStore.java  — default Panache impl (@ApplicationScoped)
│       │       └── JpaAuditEntryStore.java — default Panache impl (@ApplicationScoped)
│       ├── multiinstance/
│       │   ├── MultiInstanceSpawnService.java — creates parent + spawn group + N children in one @Transactional
│       │   ├── MultiInstanceGroupPolicy.java  — OCC counter update and M-of-N threshold evaluation
│       │   ├── MultiInstanceCoordinator.java  — @ObservesAsync entry point with retry on transient failures
│       │   ├── PoolAssignmentStrategy.java    — InstanceAssignmentStrategy: PENDING pool, first-claim-wins
│       │   ├── ExplicitListAssignmentStrategy.java — InstanceAssignmentStrategy: one child per named assignee
│       │   ├── RoundRobinAssignmentStrategy.java   — InstanceAssignmentStrategy: round-robin over candidate list
│       │   └── CompositeInstanceAssignmentStrategy.java — delegates to configured strategy
│       ├── service/
│       │   ├── WorkItemService.java       — lifecycle management, expiry, delegation; completeFromSystem/rejectFromSystem for system-context transitions
│       │   ├── WorkItemAssignmentService.java — assignment orchestration via WorkBroker
│       │   ├── WorkItemSpawnService.java  — implements SpawnPort; creates children from templates, wires PART_OF, stores callerRef
│       │   └── JpaWorkloadProvider.java   — implements WorkloadProvider via JPA store
│       └── api/
│           ├── WorkItemResource.java      — REST API at /workitems
│           ├── WorkItemSpawnResource.java — POST /workitems/{id}/spawn, GET/DELETE /workitems/{id}/spawn-groups
│           └── SpawnGroupResource.java    — GET /spawn-groups/{id}
├── deployment/                            — Extension deployment (build-time) module
│   └── src/main/java/io/casehub/work/deployment/
│       └── WorkItemsProcessor.java        — @BuildStep: FeatureBuildItem
├── testing/                               — Test utilities module (casehub-work-testing)
│   └── src/main/java/io/casehub/work/testing/
│       ├── InMemoryWorkItemStore.java     — ConcurrentHashMap-backed, no datasource needed
│       └── InMemoryAuditEntryStore.java   — list-backed
├── docs/
│   ├── DESIGN.md                          — Implementation-tracking design document
│   └── specs/
│       └── 2026-04-14-tarkus-design.md   — Primary design specification
└── HANDOFF.md                             — Session context for resumption
```

**Integration modules (built):**
- `work-flow/` — Quarkus-Flow CDI bridge (`HumanTaskFlowBridge`, `PendingWorkItemRegistry`, `WorkItemFlowEventListener`)
- `casehub-work-ledger/` — optional accountability module (command/event ledger, hash chain, attestation, EigenTrust)
- `casehub-work-queues/` — optional label-based queue module (`WorkItemFilter`, `FilterChain`, `QueueView`, `WorkItemQueueState`)
  - `api/`: `FilterResource` (/filters), `QueueResource` (/queues), `QueueStateResource` (/workitems/{id}/relinquishable)
  - `model/`: `FilterScope`, `FilterAction`, `WorkItemFilter`, `FilterChain`, `QueueView`, `WorkItemQueueState`
  - `service/`: `WorkItemExpressionEvaluator` SPI, `ExpressionDescriptor`, `JexlConditionEvaluator`, `JqConditionEvaluator`, `WorkItemFilterBean`, `FilterEngine`, `FilterEngineImpl`, `FilterEvaluationObserver`
- `casehub-work-ai/` — AI-native features; `LowConfidenceFilterProducer` wires confidence-gating into `FilterRegistryEngine`; `SemanticWorkerSelectionStrategy` (@Alternative @Priority(1)) for embedding-based worker scoring; depends on `casehub-work-core`
  - `skill/`: `WorkerSkillProfile` entity (V14 migration), `WorkerSkillProfileResource` (/worker-skill-profiles), `SemanticWorkerSelectionStrategy` (@Alternative @Priority(1) — auto-activates when module on classpath), `EmbeddingSkillMatcher` (cosine similarity via dev.langchain4j), `WorkerProfileSkillProfileProvider` (default, DB-backed), `CapabilitiesSkillProfileProvider` (@Alternative — joins capability tags), `ResolutionHistorySkillProfileProvider` (@Alternative — aggregates completion history)
- `casehub-work-notifications/` — optional outbound notification module. CDI observer fires after successful WorkItem lifecycle events and dispatches to configured channels. Flyway V3000.
  - `model/`: `WorkItemNotificationRule` entity — channelType, targetUrl, eventTypes (comma-sep), category (nullable = wildcard), secret (HMAC), enabled
  - `api/`: `NotificationRuleResource` (CRUD at `/workitem-notification-rules`)
  - `service/`: `NotificationDispatcher` — AFTER_SUCCESS CDI observer, async delivery via virtual threads, rule matching by eventType + category
  - `channel/`: `HttpWebhookChannel` (HMAC-SHA256 signing), `SlackNotificationChannel` (Incoming Webhooks), `TeamsNotificationChannel` (Adaptive Cards)
  - SPIs in `casehub-work-api`: `NotificationChannel` (channelType + send), `NotificationPayload` — custom channels implement `NotificationChannel` as `@ApplicationScoped` CDI bean
- `casehub-work-reports/` — optional SLA compliance reporting module. Zero cost when absent. 73 tests (68 H2 + 5 PostgreSQL via Testcontainers).
  - `api/`: `ReportResource` — `GET /workitems/reports/sla-breaches`, `/actors/{actorId}`, `/throughput?groupBy=day|week|month`, `/queue-health`
  - `service/`: `ReportService` (@CacheResult Caffeine 5-min TTL), `ThroughputBucketAggregator` (pure Java day→week/month rollup), response records (`SlaBreachReport`, `ActorReport`, `ThroughputReport`, `QueueHealthReport`)
  - Query strategy: HQL `CAST(date_trunc('day', w.createdAt) AS LocalDate)` + GROUP BY for throughput; JPQL COUNT/AVG aggregates for queue-health; JPQL GROUP BY for actor byCategory (no N+1)
- `casehub-work-examples/` — runnable scenario demos; covers ledger/audit, spawn, business-hours, each runs via `POST /examples/{name}/run`
- `integration-tests/` — `@QuarkusIntegrationTest` suite and native image validation (25 tests including 6 report smoke tests)

**Future integration modules (not yet scaffolded):**
- CaseHub adapter — lives in casehub-engine repo, not here (see `docs/architecture/LAYERING.md`)
- `casehub-work-qhorus/` — Qhorus MCP tools (`request_approval`, `check_approval`, `wait_for_approval`) (blocked: Qhorus not yet complete)
- `casehub-work-persistence-mongodb/` — MongoDB-backed `WorkItemStore`
- `casehub-work-persistence-redis/` — Redis-backed `WorkItemStore`

---

## Build and Test

```bash
# Build all modules
JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn clean install

# Run tests (work-api module — pure-Java SPI)
JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl casehub-work-api

# Run tests (work-core module — Jandex library)
JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl casehub-work-core

# Run tests (runtime module)
JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl runtime

# Run tests (ledger module)
JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl casehub-work-ledger

# Run tests (queues module)
JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl casehub-work-queues

# Run tests (examples module)
JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl casehub-work-examples

# Run specific test
JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -Dtest=ClassName -pl runtime

# Black-box integration tests (JVM mode)
JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn verify -pl integration-tests

# Native image integration tests (requires GraalVM 25)
JAVA_HOME=/Library/Java/JavaVirtualMachines/graalvm-25.jdk/Contents/Home \
  mvn verify -Pnative -pl integration-tests
```

**Use `mvn` not `./mvnw`** — maven wrapper not configured on this machine.

---

## Build Discipline (AI agents — read this before running any Maven command)

**Never run `mvn install` or `mvn test` without `-pl <module>`.** The full project has 20+ modules; a full build takes 5+ minutes and will time out in any AI tool context window. Always target the specific module you changed.

**Use the helper scripts in `scripts/` — they enforce hard timeouts and exit clearly:**

```bash
# Test a single module (90s timeout — exits with clear error if exceeded)
scripts/mvn-test <module>
scripts/mvn-test <module> -Dtest=SpecificTestClass

# Install a module to local Maven repo so dependents can resolve it (60s timeout)
scripts/mvn-install <module>

# Compile a module's main + test sources without running tests (45s timeout)
scripts/mvn-compile <module>

# Test multiple modules sequentially, fail-fast on first failure
scripts/check-build runtime casehub-work-reports
```

**Standard workflow after changing module X:**
```bash
scripts/mvn-test X                        # verify tests pass
scripts/mvn-install X                     # publish to local Maven repo
scripts/mvn-compile <dependent-of-X>      # verify dependent still compiles
```

**Never specify `timeout` > default in Bash tool calls.** Specifying a large timeout silently converts the command to a background task with output written to an unreadable temp file. The default (120s) runs synchronously. If your command needs more than 120s, break it into smaller pieces using the scripts above.

**Expected test times per module** (use as a sanity check — if a module takes longer, something is wrong):

| Module | Expected |
|---|---|
| casehub-work-api | < 5s |
| casehub-work-core | < 10s |
| runtime | < 60s |
| casehub-work-reports | < 45s |
| casehub-work-notifications | < 30s |
| casehub-work-ai | < 30s |
| casehub-work-queues | < 30s |
| casehub-work-ledger | < 30s |

---

**`casehub-ledger` prerequisite:** `casehub-work-ledger` depends on `io.casehub:casehub-ledger:0.2-SNAPSHOT` — a sibling project at `~/claude/casehub/ledger/`. If the build fails with "Could not find artifact", install it first:
```bash
JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn install -DskipTests -f ~/claude/casehub/ledger/pom.xml
```

**Format check:** CI runs `mvn -Dno-format` to skip the enforced formatter. Run `mvn` locally to apply formatting.

**Known extension build gotchas (from casehub-qhorus experience):**
- `quarkus-extension-processor` requires **Javadoc on every method** in `@ConfigMapping` interfaces, including group accessors — missing one causes a compile-time error
- The `extension-descriptor` goal validates that the deployment POM declares **all transitive deployment JARs** — run `mvn install -DskipTests` first after modifying the deployment POM
- `key` is a reserved word in H2 — avoid it as a column name in Flyway migrations
- `@QuarkusIntegrationTest` must live in a **separate module** from the extension runtime — the `quarkus-maven-plugin` build goal requires a configured datasource at augmentation time; extensions intentionally omit datasource config (use the `integration-tests/` module)
- `@Scheduled` intervals require `${property}s` syntax (MicroProfile Config), **not** `{property}s` — bare braces are silently ignored at augmentation time, causing `DateTimeParseException` at native startup
- Panache `find()` short-form WHERE clause must use **bare field names** (`assigneeId = :x`), not alias-prefixed names (`wi.assigneeId = :x`) — the alias is internal to Panache and not exposed in the condition string
- `quarkus.http.test-port=0` in test `application.properties` — add when a module has multiple `@QuarkusTest` classes; prevents intermittent `TIME_WAIT` port conflicts when Quarkus restarts between test classes
- `@TestTransaction` + REST assertions don't mix — a `@Transactional` CDI method called from within `@TestTransaction` joins the test transaction; subsequent HTTP calls run in their own transaction and cannot see the uncommitted data (returns 404). Remove `@TestTransaction` from test classes that mix direct service calls with REST Assured assertions
- If `deployment/pom.xml` declares `X-deployment` as a dependency, `runtime/pom.xml` **must** declare `X` (the corresponding runtime artifact) — the `extension-descriptor` goal enforces this pairing and fails with a misleading "Could not find artifact" error pointing at the runtime module. If `WorkItemsProcessor` doesn't use anything from `X-deployment`, remove it rather than adding an unnecessary runtime dependency.
- Optional library modules with CDI beans need `jandex-maven-plugin` in their pom — without it, Quarkus discovers their beans during their own `@QuarkusTest` run (direct class scan) but NOT when consumed as a JAR by another module. Add `io.smallrye:jandex-maven-plugin:3.3.1` with the `jandex` goal to any module that defines `@ApplicationScoped` or `@Path` beans and is not a full Quarkus extension.
- Hibernate bytecode-enhanced entities return `null`/`0` for all fields when accessed via `Field.get(entity)` reflection — Hibernate stores values in a generated subclass, not in the parent field slots. Use direct field access (`entity.fieldName`) to build context maps or projections; use a drift-protection test to catch new fields (see `JexlConditionEvaluatorTest.toMap_containsAllPublicNonStaticWorkItemFields`).
- Use `quarkus-junit` (not `quarkus-junit5`, which is deprecated and triggers a Maven relocation warning on every build). For pure-Java modules with no `@QuarkusTest`, use plain `org.junit.jupiter:junit-jupiter` instead.
- `WorkItemLifecycleEvent.source()` returns `Object` (the `WorkItem` entity), not the CloudEvents URI string — call `.sourceUri()` to get the URI. The method is inherited from `WorkLifecycleEvent` and intentionally typed `Object` so the base event is WorkItem-agnostic.
- `FilterAction.apply()` takes `Object workUnit` — implementations must cast to `WorkItem`. The filter engine now lives in `runtime/filter/` (moved from `casehub-work-core` in #133); `casehub-work-core` has no filter classes.
- `EscalationPolicy.escalate(WorkLifecycleEvent)` replaces the old two-method interface — check `event.eventType()` to distinguish `WorkEventType.EXPIRED` (ExpiryCleanupJob) from `WorkEventType.CLAIM_EXPIRED` (ClaimDeadlineJob) and handle each branch accordingly.
- `FilterRegistryEngine` observes `WorkLifecycleEvent` (the base type from `casehub-work-api`), not the workitems-specific `WorkItemLifecycleEvent` — use `WorkItemLifecycleEvent` when firing events from runtime code so the engine picks them up via CDI observer inheritance.
- `CapabilitiesSkillProfileProvider` and `ResolutionHistorySkillProfileProvider` are `@Alternative` — only `WorkerProfileSkillProfileProvider` is the default `SkillProfileProvider`. Activate the alternatives via CDI `@Alternative @Priority(1)` in your application.
- For `EmbeddingSkillMatcher`, use `dev.langchain4j:langchain4j-core` (plain Java library), NOT `io.quarkiverse.langchain4j:quarkus-langchain4j-core` (Quarkus extension) — the extension causes `@QuarkusTest` augmentation to stall when no provider is configured.
- `callerRef` on `WorkItem` is opaque — quarkus-work stores and echoes it in every `WorkItemLifecycleEvent`; it never interprets it. CaseHub embeds its `planItemId` here so child completion events can be routed back to the right `PlanItem` without a query.
- Spawn group cascade cancellation is scoped to the specific group via `createdBy = "system:spawn:{groupId}"` — `DELETE /workitems/{id}/spawn-groups/{gid}?cancelChildren=true` cancels only children from that group, not all children of the parent.
- Spawn idempotency key is scoped per parent — the same key on different parents creates separate groups; uniqueness is `(parent_id, idempotency_key)`.
- `casehub-work-ledger` depends on `io.casehub:casehub-ledger:0.2-SNAPSHOT` — update this version when `casehub-ledger` changes its own version.
- `@CacheResult` on `ReportService` methods accepts nullable parameters — Quarkus 3.x `CompositeCacheKey` handles nulls correctly via `Arrays.hashCode`; the cache key for `slaBreaches(null, null, null, null)` is stable and shared across unfiltered calls. Use a `from` filter in tests that call the endpoint unfiltered AND need fresh data (cache TTL is 1s in test `application.properties`).
- `PostgresDialectValidationTest` runs against a real PostgreSQL Testcontainer via a dedicated Surefire execution (`postgres-dialect-test`) that: (1) sets `quarkus.datasource.db-kind=postgresql` as a **system property** before augmentation (test resource overrides don't reach the augmentation cache check), (2) uses `reuseForks=false` for a clean JVM so the fresh augmentation uses PostgreSQL, (3) runs **first** before H2 tests so no cached H2 artifact exists yet. `PostgresTestResource` starts the container and injects the JDBC URL. Flyway is disabled in the PostgreSQL test and replaced with `hibernate-orm.database.generation=drop-and-create` because the Flyway migrations use H2-permissive SQL (e.g. bare `DOUBLE` type) that PostgreSQL rejects — this is a known production compatibility issue to address separately.
- `CAST(date_trunc('day', w.createdAt) AS LocalDate)` in HQL — the explicit `CAST AS LocalDate` ensures Hibernate 6 returns `java.time.LocalDate` in the result set regardless of dialect, avoiding type ambiguity between H2 and PostgreSQL.
- `@ObservesAsync` CDI observers with `@Transactional` logic should delegate to a separate injected `@ApplicationScoped @Transactional` bean rather than annotating the observer method itself. In Quarkus, calling a `@Transactional` method from a non-transactional context (the `@ObservesAsync` method) correctly starts a new transaction per call — enabling clean OCC retry. If the observer itself were `@Transactional`, self-invocation issues and rollback semantics would be much harder to manage. See `MultiInstanceCoordinator` + `MultiInstanceGroupPolicy`.
- When JTA commit fails with OCC (`OptimisticLockException`), Quarkus/Narayana may propagate it wrapped as a `RollbackException` rather than as the raw `jakarta.persistence.OptimisticLockException`. Catching only `OptimisticLockException` in retry loops will miss these cases — catch `Exception` broadly and handle accordingly.
- `fireAsync()` inside a `@Transactional` method dispatches the event immediately to the thread pool — it does NOT wait for the transaction to commit. If the transaction later rolls back (e.g. OCC), the event has already been delivered. Keep `fireAsync()` outside the transaction boundary (call it after the transactional method returns) when the event should only fire on successful commit.
- `WorkItemSpawnGroup.findMultiInstanceByParentId()` — returns the multi-instance spawn group (where `requiredCount IS NOT NULL`). A parent can have multiple spawn groups from repeated spawn calls; this method gets the multi-instance one specifically.
- `scanRoots()` in `JpaWorkItemStore` uses a depth-1 ancestor lookup (not a recursive CTE) for H2 compatibility. Coordinator parents surface in the inbox via their children's candidateGroups/Users. The inbox always returns `parentId IS NULL` items only — children never appear directly.
- `completeFromSystem()` and `rejectFromSystem()` in `WorkItemService` accept any non-terminal status. Use these (not `complete()`/`reject()`) when transitioning a WorkItem from system context (e.g., multi-instance coordinator completing the parent which may be PENDING).

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
**GitHub repo:** casehubio/work

**Active epics** — priority order for market leadership:

| Priority | # | Epic | Status | First child |
|---|---|---|---|---|
| 1 | #101 | Business-Hours Deadlines — SLA in working hours | ✅ complete | BusinessCalendar, HolidayCalendar SPIs; DefaultBusinessCalendar, ICalHolidayCalendar, HolidayCalendarProducer; V19 migration; expiresAtBusinessHours/claimDeadlineBusinessHours on template + request; example scenario |
| 2 | #103 | Notifications — Slack/Teams/webhook on lifecycle events | ✅ complete | #140 ✅ SPI+dispatcher+CRUD, #141 ✅ HTTP/Slack/Teams channels |
| ✅ | #104 | SLA Compliance Reporting — breach rates, actor performance | ✅ complete | `casehub-work-reports` optional module; sla-breaches, actors, throughput, queue-health; 73 tests (68 H2 + 5 PostgreSQL) |
| ✅ | #106 | Multi-Instance Tasks — M-of-N parallel completion | ✅ complete | `MultiInstanceSpawnService`, `MultiInstanceCoordinator`, `MultiInstanceGroupPolicy`; `InstanceAssignmentStrategy` SPI + 3 impls; threaded inbox via `scanRoots()`; `GET /workitems/{id}/instances`; V20+V21 migrations |
| — | #147 | Project Refinement — architecture and doc improvements | open | #148 DESIGN.md split, #149 WorkItemService decompose, #150 broadcaster SPI (unblocks #93), #151 Flyway convention, #152 examples split |
| — | #92 | Distributed WorkItems — clustering + federation | future | #93 (SSE) implementable now; blocked on #150 (broadcaster SPI) |
| — | #79 | External System Integrations | blocked | CaseHub/Qhorus not stable |
| — | #39 | ProvenanceLink (PROV-O causal graph) | blocked | Awaiting #79 |
| ✅ | #100 | AI-Native Features — confidence gating, semantic routing | complete | #112–#126 all done |
| ✅ | #102 | Workload-Aware Routing — least-loaded assignment | complete | #115, #116. RoundRobinStrategy deferred (#117). |
| ✅ | #105 | Subprocess Spawning | complete | #127–#132 all done |
| ✅ | #98 | Form Schema — payload/resolution JSON Schema | complete | #107 ✅, #108 ✅ |
| ✅ | #99 | Audit History Query API — cross-WorkItem search | complete | #109 ✅, #110 ✅, #111 ✅ |
| ✅ | #77,78,80,81 | Collaboration, Queue Intelligence, Storage, Platform | complete | — |

**Automatic behaviours (Claude follows these at all times in this project):**
- **Before implementation begins** — check if an active issue exists. If not, run issue-workflow Phase 1 before writing any code. Create a child issue under the matching epic above.
- **Before any commit** — run issue-workflow Phase 3 to confirm issue linkage.
- **All commits should reference an issue** — `Refs #N` (ongoing) or `Closes #N` (done). Also reference the parent epic: `Refs #77` etc.
- **Code review fix commits** — when committing fixes found during a code review, create or reuse an issue for that review work **before** committing. Use `Refs #N` on the relevant epic even if it is already closed.
- **New feature requests** — assess which epic it belongs to before creating the issue. If none fits, propose a new epic first.

## Ecosystem Conventions

All casehubio projects align on these conventions:

**Quarkus version:** All projects use `3.32.2`. When bumping, bump all projects together.

**GitHub Packages — dependency resolution:** Add to `pom.xml` `<repositories>`:
```xml
<repository>
  <id>github</id>
  <url>https://maven.pkg.github.com/casehubio/*</url>
  <snapshots><enabled>true</enabled></snapshots>
</repository>
```
CI must use `server-id: github` + `GITHUB_TOKEN` in `actions/setup-java`.

**Cross-project SNAPSHOT versions:** `casehub-ledger` and `casehub-work` modules are `0.2-SNAPSHOT` resolved from GitHub Packages. Declare in `pom.xml` properties and `<dependencyManagement>` — no hardcoded versions in submodule poms.

