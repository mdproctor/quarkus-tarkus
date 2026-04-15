# quarkus-workitems-flow

Integrates Quarkus WorkItems with [Quarkus-Flow](https://github.com/quarkiverse/quarkus-flow),
allowing workflow definitions to include steps that suspend until a human or AI agent
acts on a WorkItem.

Add this module when your Quarkus-Flow workflows need to pause and wait for a human decision —
an approval, a review, an override — before continuing.

---

## Dependency

```xml
<dependency>
  <groupId>io.quarkiverse.workitems</groupId>
  <artifactId>quarkus-workitems-flow</artifactId>
  <version>1.0.0-SNAPSHOT</version>
</dependency>
```

Also requires `quarkus-workitems` (the core extension) and a configured datasource.

---

## How it works

When a workflow reaches a WorkItems step:

1. A WorkItem is created in the WorkItems inbox via `WorkItemService`
2. The workflow suspends — Quarkus-Flow holds the coroutine; no thread is blocked
3. A human (or agent) acts on the WorkItem via the WorkItems REST API (`PUT /workitems/{id}/complete`)
4. `WorkItemLifecycleEvent` fires; `WorkItemFlowEventListener` picks it up and completes the suspended future
5. The workflow resumes with the resolution JSON as the step output

Rejection or cancellation fails the future with a `WorkItemResolutionException`,
which the workflow can catch and handle.

---

## Usage — WorkItemsFlow DSL (recommended)

Extend `WorkItemsFlow` instead of `Flow` to get the `workItem()` builder alongside
`function()`, `agent()`, and other Quarkus-Flow task types:

```java
@ApplicationScoped
public class DocumentApprovalWorkflow extends WorkItemsFlow {

    @Override
    public Workflow descriptor() {
        return workflow("document-approval")
                .tasks(
                        workItem("legalReview")
                                .title("Legal review required")
                                .description("Review the attached contract for compliance issues.")
                                .candidateGroups("legal-team")
                                .priority(WorkItemPriority.HIGH)
                                .payloadFrom((DocumentDraft d) -> d.toJson())
                                .buildTask(DocumentDraft.class))
                .build();
    }
}
```

The `workItem()` call creates a `WorkItemTaskBuilder`. Calling `.buildTask(InputClass.class)`
produces a `FuncTaskConfigurer` that Quarkus-Flow wires into the workflow like any other task.

### WorkItemTaskBuilder options

| Method | Required | Description |
|---|---|---|
| `.title(String)` | ✅ | Shown in the WorkItems inbox |
| `.description(String)` | | Longer context for the reviewer |
| `.assigneeId(String)` | | Assign directly to a specific user |
| `.candidateGroups(String)` | | Route to a work queue (comma-separated group names) |
| `.priority(WorkItemPriority)` | | Defaults to `NORMAL` |
| `.payloadFrom(Function<T, String>)` | | Extracts JSON context from the workflow step input |

Use either `.assigneeId()` or `.candidateGroups()`, not both. `candidateGroups` enables
work-queue routing — any eligible member can claim the WorkItem from the inbox.

---

## Usage — HumanTaskFlowBridge (lower-level)

Inject `HumanTaskFlowBridge` directly if you prefer not to extend `WorkItemsFlow`,
or when you need more control over WorkItem creation:

```java
@Inject
HumanTaskFlowBridge humanTask;

// In a workflow function task:
tasks.function("get-approval", ctx ->
    humanTask.requestApproval(
        "Review document",
        "Please check for compliance issues.",
        "alice",
        WorkItemPriority.HIGH,
        ctx.input().toJson())
)
```

Two methods are available:

- `requestApproval(title, description, assigneeId, priority, payload)` — direct assignment
- `requestGroupApproval(title, description, candidateGroups, priority, payload)` — work-queue routing

Both return `Uni<String>` — the resolution JSON when the WorkItem is completed,
or a failed Uni with `WorkItemResolutionException` if rejected or cancelled.

---

## Class overview

| Class | Role |
|---|---|
| `WorkItemsFlow` | Base class — extend instead of `Flow` to access the `workItem()` DSL method |
| `WorkItemTaskBuilder` | Fluent builder returned by `workItem()` — configures the WorkItem and produces a `FuncTaskConfigurer` |
| `HumanTaskFlowBridge` | CDI bean — creates WorkItems and registers their futures; inject directly for lower-level control |
| `PendingWorkItemRegistry` | Internal — holds `CompletableFuture<String>` per pending WorkItem; resolved when the human acts |
| `WorkItemFlowEventListener` | Internal — observes `WorkItemLifecycleEvent` CDI events and completes or fails pending futures |
| `WorkItemResolutionException` | Thrown when a WorkItem is rejected or cancelled; carry the WorkItem ID and reason |

`PendingWorkItemRegistry` and `WorkItemFlowEventListener` are internal infrastructure.
They are `@ApplicationScoped` CDI beans but are not part of the public API.

---

## Handling rejection

If the WorkItem is rejected or cancelled, the `Uni` fails with `WorkItemResolutionException`.
Handle it in the workflow like any other task failure:

```java
workItem("legalReview")
        .title("Legal review required")
        .candidateGroups("legal-team")
        .buildTask(DocumentDraft.class)
// Quarkus-Flow error handling applies here — use .onFailure() or workflow-level error handlers
```

The exception carries `.workItemId()` and `.reason()` for logging or compensation steps.

---

## Relationship to quarkus-workitems

`quarkus-workitems-flow` depends on `quarkus-workitems` — it does not modify the core extension.
`WorkItemService` and the CDI event infrastructure come from the core. If you use
`quarkus-workitems` without this module, none of these classes are on the classpath.
