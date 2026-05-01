# CaseHub Work — Implementation Tracker

Architecture, domain model, and SPI contracts: [`docs/ARCHITECTURE.md`](ARCHITECTURE.md)
REST API reference: [`docs/api-reference.md`](api-reference.md)
Configuration properties: [`README.md`](../README.md#configuration)

---

## Build Roadmap

| Phase | Status | What |
|---|---|---|
| **1 — Core data model** | ✅ Complete | Storage SPI, JPA defaults, InMemory (testing module), WorkItem + AuditEntry entities, Flyway V1+V2+V3, WorkItemService, WorkItemsConfig, WorkItemLabel (MANUAL/INFERRED), LabelVocabulary + LabelDefinition |
| **2 — REST API** | ✅ Complete | WorkItemResource — 13 endpoints, DTOs, exception mappers |
| **3 — Lifecycle engine** | ✅ Complete | ExpiryCleanupJob, ClaimDeadlineJob, EscalationPolicy SPI + 3 implementations |
| **4 — CDI events** | ✅ Complete | WorkItemLifecycleEvent on all transitions; rationale + planRef fields |
| **5 — Quarkus-Flow integration** | ✅ Complete | `work-flow` — HumanTaskFlowBridge, Uni<String> suspension |
| **6 — Ledger module** | ✅ Complete | `casehub-work-ledger` — command/event model, hash chain, attestation, EigenTrust; optional, zero core impact |
| **7 — Label-based queues** | ✅ Complete | `casehub-work-queues` — label model (MANUAL/INFERRED), vocabulary (GLOBAL→PERSONAL scopes), filter engine (JEXL/JQ/Lambda, multi-pass propagation, cascade delete via FilterChain), QueueView named queries, soft assignment |
| **8 — Native image** | ✅ Complete | GraalVM 25 native build, `@QuarkusIntegrationTest` suite, 0.084s startup |
| **9 — Form Schema** | ✅ Complete | Epic #98: `WorkItemFormSchema` entity + CRUD API (#107 ✅), payload/resolution validation (#108 ✅) |
| **10 — Audit History Query API** | ✅ Complete | Epic #99: `GET /audit` cross-WorkItem query with actorId/event/date/category filters + pagination (#109 ✅), SLA breach report (#110 ✅), actor performance summary (#111 ✅). V12 indexes. |
| **11 — Confidence-Gated Routing** | ✅ Complete | Epic #100: `confidenceScore` on WorkItem + V13 (#112 ✅), `FilterAction` SPI + JEXL engine + permanent/dynamic registry (#113 ✅), `casehub-work-ai` `LowConfidenceFilterProducer` (#114 ✅) |
| **12 — WorkerSelectionStrategy** | ✅ Complete | Epics #100/#102: `casehub-work-api` shared SPI module (#115 ✅), `WorkItemAssignmentService` + `LeastLoadedStrategy` + `ClaimFirstStrategy` + `NoOpWorkerRegistry` (#116 ✅) |
| **13 — casehub-work separation** | ✅ Complete | `casehub-work-api` (shared SPI contracts) and `casehub-work-core` (WorkBroker + generic filter engine) extracted. Issue #118. |
| **13b — Semantic Skill Matching** | ✅ Complete | `SkillProfile` + `SkillProfileProvider` + `SkillMatcher` SPIs. `EmbeddingSkillMatcher`, `WorkerProfileSkillProfileProvider`, `SemanticWorkerSelectionStrategy` auto-activates via `@Alternative @Priority(1)`. `WorkerSkillProfile` entity + REST API. Flyway V14. Issue #121. |
| **14 — SLA Compliance Reporting** | ✅ Complete | Epic #104: `casehub-work-reports` optional module — sla-breaches, actors, throughput, queue-health. HQL `date_trunc`. `@CacheResult` Caffeine 5-min TTL. 73 tests. (#142–#145 ✅) |
| **15 — Multi-Instance WorkItems** | ✅ Complete | Epic #106: Template-driven M-of-N parallel instances. `MultiInstanceSpawnService`, `MultiInstanceCoordinator`, `MultiInstanceGroupPolicy` (OCC). `InstanceAssignmentStrategy` SPI + 3 impls. `GET /workitems/{id}/instances`. V20+V21 migrations. |
| **16 — Business-Hours Deadlines** | ✅ Complete | Epic #101: `BusinessCalendar` + `HolidayCalendar` SPIs. `DefaultBusinessCalendar`, `ICalHolidayCalendar`. V19 migration. |
| **17 — Notifications** | ✅ Complete | Epic #103: `casehub-work-notifications` — HTTP webhook, Slack, Teams. `NotificationChannel` SPI. Flyway V3000. (#140–#141 ✅) |
| **18 — Broadcaster SPI** | ✅ Complete | Epic #147/#150: `WorkItemEventBroadcaster` and `WorkItemQueueEventBroadcaster` extracted as interfaces. `LocalWorkItem*EventBroadcaster` default impls with `@DefaultBean`. Unblocks #93. |
| **19 — Distributed SSE** | ✅ Complete | Epic #92/#93: `casehub-work-postgres-broadcaster` — `PostgresWorkItemEventBroadcaster` (`@Alternative @Priority(1)`) via PostgreSQL LISTEN/NOTIFY (`casehub_work_events` channel). `WorkItemEventPayload` wire DTO. `WorkItemLifecycleEvent.fromWire(...)` static factory added to runtime. AFTER_SUCCESS observer — rolled-back events not published. 22 tests. Follow-up #155 for queue broadcaster. |
| **— CaseHub integration** | ⏸ Blocked | `casehub-work-casehub` — CaseHub WorkerRegistry adapter (awaiting CaseHub stable API) |
| **— Qhorus integration** | ⏸ Blocked | `casehub-work-qhorus` — MCP tools (awaiting Qhorus stable API) |
| **— ProvenanceLink** | ⏸ Blocked | Typed PROV-O causal graph — awaiting CaseHub + Qhorus integrations (#39) |
| **20 — Queue Broadcaster** | ✅ Complete | Epic #92/#155: `casehub-work-queues-postgres-broadcaster` — `PostgresWorkItemQueueEventBroadcaster` (`@Alternative @Priority(1)`) via PostgreSQL LISTEN/NOTIFY (`casehub_work_queue_events` channel). `WorkItemQueueEvent` plain record — no wire DTO needed. AFTER_SUCCESS observer. 13 tests (7 unit + 6 `@QuarkusTest` + Testcontainer). No Flyway migrations. |

---

## Flyway Migration History

| Version | Module | Description |
|---|---|---|
| V1–V21 | runtime | Sequential — initial schema through multi-instance support |
| V14 | casehub-work-ai | Worker skill profile (fills deliberate gap in runtime sequence) |
| V2000–V2002 | casehub-work-queues / casehub-work-ledger | Queue membership tracker, ledger supplement |
| V3000 | casehub-work-notifications | Notification rules |
| V4001 | casehub-work-ai | AI capability extensions |
| V5000 | casehub-work-issue-tracker | Issue link schema |
| *(none)* | casehub-work-postgres-broadcaster | No schema changes — uses the datasource already managed by the core extension |
| *(none)* | casehub-work-queues-postgres-broadcaster | No schema changes — reuses datasource from `casehub-work-queues` |

See CLAUDE.md **Flyway Migration Conventions** for the version-range allocation rule.

---

## Testing Strategy

Three tiers:

**Unit tests** (no Quarkus boot):
- Use `InMemoryWorkItemStore` from `casehub-work-testing`
- Pure logic functions
- No datasource, no Flyway, instant execution

**Integration tests** (`@QuarkusTest`):
- H2 in-memory datasource; Flyway runs all migrations at boot
- `@TestTransaction` per test method — each test rolls back, no data leakage
- Exercise full stack: REST → Service → JPA → H2

**Native image tests** (`@QuarkusIntegrationTest`):
- Black-box testing against a built native image
- End-to-end validation

**Current test totals (all modules, 0 failures):**

| Module | Tests |
|---|---|
| casehub-work-api | 15 |
| casehub-work-core | 38 |
| runtime | 638 |
| work-flow | 32 |
| casehub-work-ledger | 75 |
| casehub-work-queues | 82 |
| casehub-work-ai | 48 |
| casehub-work-examples | 37 |
| casehub-work-reports | 73 |
| casehub-work-notifications | ~30 |
| casehub-work-postgres-broadcaster | 22 |
| casehub-work-queues-postgres-broadcaster | 13 |
| casehub-work-issue-tracker | 23 |
| testing | 16 |
| integration-tests | 25 |
| **Total** | **~1167+** |
