package io.quarkiverse.workitems.examples.queues.lifecycle;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import io.quarkiverse.workitems.queues.event.QueueEventType;
import io.quarkiverse.workitems.queues.model.FilterScope;
import io.quarkiverse.workitems.queues.model.QueueView;
import io.quarkiverse.workitems.runtime.model.WorkItem;
import io.quarkiverse.workitems.runtime.model.WorkItemCreateRequest;
import io.quarkiverse.workitems.runtime.model.WorkItemPriority;
import io.quarkiverse.workitems.runtime.service.WorkItemService;

/**
 * Runnable example demonstrating the full queue lifecycle event sequence:
 * {@code ADDED → CHANGED → REMOVED}.
 *
 * <h2>Scenario</h2>
 * <ol>
 * <li>A {@link QueueView} named "Lifecycle Demo Queue" is created with the pattern
 * {@code lifecycle-demo/**}.</li>
 * <li>A WorkItem is created with the MANUAL label {@code lifecycle-demo/case-1}.
 * The filter engine runs and determines the item now belongs to the queue →
 * {@link QueueEventType#ADDED} fires.</li>
 * <li>The WorkItem is claimed by an assignee. The filter engine re-evaluates
 * (stripping and re-applying INFERRED labels). The MANUAL label is not affected,
 * so the item remains in the queue → {@link QueueEventType#CHANGED} fires.</li>
 * <li>The MANUAL label is removed. The filter engine runs and finds no matching labels →
 * {@link QueueEventType#REMOVED} fires.</li>
 * </ol>
 *
 * <h2>Key insight — why not REMOVED+ADDED during step 3?</h2>
 * <p>
 * When the filter engine runs, it strips INFERRED labels and re-applies them. Without
 * coordination, this would emit REMOVED (during strip) then ADDED (on re-apply) for the
 * same queue within the same operation — a false signal. The {@code QueueMembershipContext}
 * suppresses this by comparing before vs after states at the operation boundary. Items
 * that remain in a queue after re-evaluation get a single CHANGED event instead.
 *
 * <h2>Running</h2>
 *
 * <pre>
 *   curl -s -X POST http://localhost:8080/queue-examples/lifecycle/run | jq .
 * </pre>
 *
 * <h2>Why persistent membership tracking matters</h2>
 * <p>
 * The "before-state" used by each step comes from the {@code work_item_queue_membership}
 * DB table — not from an in-memory cache. If the JVM restarts between steps, the correct
 * events still fire because the last-known membership state is persisted.
 */
@Path("/queue-examples/lifecycle")
@Produces(MediaType.APPLICATION_JSON)
public class QueueLifecycleScenario {

    @Inject
    WorkItemService workItemService;

    @Inject
    QueueEventLog eventLog;

    /**
     * Run the lifecycle scenario and return a step-by-step trace of queue events.
     *
     * <p>
     * Each step record names the operation, which queue was affected, and which event type
     * fired. The response makes it easy to verify that ADDED/CHANGED/REMOVED fire in the
     * correct order with no spurious duplicates.
     *
     * @return scenario result containing all captured queue events grouped by step
     */
    @POST
    @Path("/run")
    public QueueLifecycleResponse run() {
        eventLog.clear();
        final List<QueueLifecycleResponse.Step> steps = new ArrayList<>();
        final String queuePattern = "lifecycle-demo/**";

        // ── Setup ─────────────────────────────────────────────────────────────
        // Create the queue view that the scenario exercises.
        // In a real application this would be configured once at startup.
        final QueueView queue = createQueueView("Lifecycle Demo Queue", queuePattern);
        final UUID queueId = queue.id;

        // ── Step 1: ADDED ─────────────────────────────────────────────────────
        // Creating the WorkItem with a MANUAL label matching the queue triggers
        // the filter engine. The before-state has no queue membership (new item),
        // the after-state has the queue → ADDED fires.
        final WorkItemCreateRequest createReq = new WorkItemCreateRequest(
                "Queue lifecycle demo item", null, "demo", null,
                WorkItemPriority.HIGH, null, null, null, null,
                "demo", null, null, null, null, null);
        final WorkItem wi = workItemService.create(createReq);
        final UUID itemId = wi.id;

        // Add the MANUAL label that makes the item a member of the queue.
        workItemService.addLabel(itemId, "lifecycle-demo/case-1", "demo");
        steps.add(new QueueLifecycleResponse.Step(
                1,
                "WorkItem created and labelled 'lifecycle-demo/case-1'",
                "Before: not in queue. After: in queue → ADDED",
                captureEvents(queueId, itemId)));

        // ── Step 2: CHANGED ───────────────────────────────────────────────────
        // Claiming the item fires a WorkItemLifecycleEvent. The filter engine
        // re-evaluates: INFERRED labels are stripped (none here) and re-applied.
        // The MANUAL label survives the strip. Before = in queue, after = in queue → CHANGED.
        workItemService.claim(itemId, "alice");
        steps.add(new QueueLifecycleResponse.Step(
                2,
                "WorkItem claimed by 'alice'",
                "Before: in queue. INFERRED labels stripped+re-applied. MANUAL survives. After: still in queue → CHANGED",
                captureEvents(queueId, itemId)));

        // ── Step 3: CHANGED again (started) ───────────────────────────────────
        workItemService.start(itemId, "alice");
        steps.add(new QueueLifecycleResponse.Step(
                3,
                "WorkItem started by 'alice'",
                "Before: in queue. Filter re-evaluates. MANUAL label still present. After: still in queue → CHANGED",
                captureEvents(queueId, itemId)));

        // ── Step 4: REMOVED ───────────────────────────────────────────────────
        // Removing the MANUAL label fires LABEL_REMOVED → filter engine runs.
        // Before = in queue (label was there). After = no matching labels → not in queue → REMOVED.
        workItemService.removeLabel(itemId, "lifecycle-demo/case-1");
        steps.add(new QueueLifecycleResponse.Step(
                4,
                "MANUAL label 'lifecycle-demo/case-1' removed",
                "Before: in queue. After: no matching labels → not in queue → REMOVED",
                captureEvents(queueId, itemId)));

        return new QueueLifecycleResponse("queue-lifecycle-demo", steps);
    }

    @Transactional
    QueueView createQueueView(final String name, final String pattern) {
        final QueueView queue = new QueueView();
        queue.name = name;
        queue.labelPattern = pattern;
        queue.scope = FilterScope.ORG;
        queue.persist();
        return queue;
    }

    private List<QueueEventType> captureEvents(final UUID queueId, final UUID itemId) {
        return eventLog.drain().stream()
                .filter(e -> e.queueViewId().equals(queueId) && e.workItemId().equals(itemId))
                .map(QueueEventLog.Entry::eventType)
                .toList();
    }
}
